/**
 * 
 */
package org.sjcdigital.services;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jsoup.Connection.Response;
import org.jsoup.nodes.Document;
import org.sjcdigital.model.constants.Tipos;
import org.sjcdigital.model.entity.Proponentes;
import org.sjcdigital.model.entity.Proposicoes;
import org.sjcdigital.model.repositories.ProponenteRepository;
import org.sjcdigital.model.repositories.ProposicoesRepository;
import org.sjcdigital.utils.ParserUtils;
import org.sjcdigital.utils.ScrapperUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pedro-hos@outlook.com
 *
 */

@ApplicationScoped
public class ProposicoesBot {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ProposicoesBot.class);
	
	@ConfigProperty(name = "url.camara")
	String url;
	
	@ConfigProperty(name = "query.param.busca")
	String queryBuscaAvancada;
	
	@ConfigProperty(name = "query.param.inicio")
	String queryInicio;
	
	@ConfigProperty(name = "query.param.final")
	String queryFinal;
	
	@ConfigProperty(name = "form.param.viewstate")
	String viewStateParam;
	
	@ConfigProperty(name = "form.param.eventvalidation")
	String eventValidationParam;
	
	@ConfigProperty(name = "form.param.xls")
	String csvParam;
	
	@ConfigProperty(name = "form.param.xls.value")
	String csvParamValue;
	
	@ConfigProperty(name = "location.files")
	String path;
	
	@Inject
	ProponenteService proponenteService;
	
	@Inject
	ProponenteRepository proponenteRepository;
	
	@Inject
	ProposicoesRepository proposicoesRepository;
	
	@Inject
	ScrapperUtils scrapperUtils;
	
	/**
	 * Isso será um cron para pegar todo inicio de mes o do mes anterio.
	 */
	public void buscaProposicoesXLS() {
		
			try {
				
				Map<String, Map<String, String>> parameters = montaParameters();
				Path path = scrapperUtils.downloadXLS(montaURLMesAnterior(), parameters.get("formParameters"), parameters.get("cookies"));
				extractDataFromXLSX(path.toString());
				
			} catch (IOException | URISyntaxException | InterruptedException e) {
				e.printStackTrace();
			}
		
	}
	
	private Map<String, Map<String, String>> montaParameters() throws IOException {
		
		Map<String, Map<String, String>> paramenters = new HashMap<String, Map<String, String>>();
		Map<String, String> values = new HashMap<String, String>();
		
		Response response = scrapperUtils.getResponse(montaURLMesAnterior());
		paramenters.put("cookies", response.cookies());
		paramenters.put("formParameters", values);
		
		Document doc = response.parse();
		values.put(csvParam, csvParamValue);
		values.put(viewStateParam, doc.getElementById(viewStateParam).val());
		values.put(eventValidationParam, doc.getElementById(eventValidationParam).val());
		
		return paramenters;
	}
	
	private String montaURLPorData(final String startDate, final String endDate) {
		LOGGER.info("Getting data from " + startDate + " to " + endDate);
		return url + queryBuscaAvancada + queryInicio + startDate + queryFinal + endDate;
	}
	
	private String montaURLMesAnterior() {
		
		DateTimeFormatter pattern = DateTimeFormatter.ofPattern("dd/MM/yyyy");
		LocalDate initial = LocalDate.now().minusMonths(1);
		
		String start = initial.withDayOfMonth(1).format(pattern);
		String end = initial.withDayOfMonth(initial.lengthOfMonth()).format(pattern);
		
		return montaURLPorData(start, end);
		
	}
	
	/**
	 * 
	 * @param filePath
	 */
	@Transactional
	public void extractDataFromXLSX(final String filePath) {
		
		try {
			
			Workbook workbook = WorkbookFactory.create(new File(filePath));
			
			// Getting the Sheet at index zero
	        Sheet sheet = workbook.getSheetAt(0);
	        
	        // Create a DataFormatter to format and get each cell's value as String
	        DataFormatter dataFormatter = new DataFormatter();
	        
	        sheet.forEach(row -> {

	        	if (row.getRowNum() != 0) {
		        	Proposicoes prop = new Proposicoes();
		        	
		        	prop.processo = ParserUtils.convertToInteger(dataFormatter.formatCellValue(row.getCell(0)));
					prop.ano = Integer.valueOf(dataFormatter.formatCellValue(row.getCell(1)));
					prop.tipo = Tipos.findByText(dataFormatter.formatCellValue(row.getCell(2))).orElseThrow();
					prop.situacao = dataFormatter.formatCellValue(row.getCell(3));
					prop.ementa = dataFormatter.formatCellValue(row.getCell(4));
					prop.protocolo = ParserUtils.convertToInteger(dataFormatter.formatCellValue(row.getCell(5)));
					prop.data = ParserUtils.convertToLocalDate(dataFormatter.formatCellValue(row.getCell(6)));
					
					prop.proponetes = saveOrGetProponent(proponenteService.buscaProponentesPagina(prop.processo, prop.ano, prop.tipo));
					
					LOGGER.info(prop.toString());
					
					proposicoesRepository.persist(prop);
	        	}
	        	
	        });
	        
	        workbook.close();
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		
	}

	/**
	 * @param buscaProponentesPagina
	 * @return
	 */
	@Transactional
	private List<Proponentes> saveOrGetProponent(List<Proponentes> buscaProponentesPagina) {
		
		List<Proponentes> nova = new ArrayList<>();
		
		for (Proponentes proponentes : buscaProponentesPagina) {
			
			Proponentes findByNome = proponenteRepository.findByNome(proponentes.nome);
			
			if(findByNome != null) {
				nova.add(findByNome);
			} else {
				proponenteRepository.persist(proponentes);
				nova.add(proponentes);
			}
		}
		
		return nova;
	}
	
}
