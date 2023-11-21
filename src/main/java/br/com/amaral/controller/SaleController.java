package br.com.amaral.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.amaral.ExceptionProject;
import br.com.amaral.constant.APITokens;
import br.com.amaral.model.ProductSold;
import br.com.amaral.model.Sale;
import br.com.amaral.model.dto.MelhorEnvioShippingDTO;
import br.com.amaral.model.dto.ProductSoldDTO;
import br.com.amaral.model.dto.SaleDTO;
import br.com.amaral.model.dto.MelhorEnvioShippingCompanyDTO;
import br.com.amaral.model.dto.MelhorEnvioTrackingDataDTO;
import br.com.amaral.model.dto.MelhorEnvioTrackingDataProductsDTO;
import br.com.amaral.model.dto.MelhorEnvioTrackingDataTagsDTO;
import br.com.amaral.model.dto.MelhorEnvioTrackingDataVolumesDTO;
import br.com.amaral.repository.ISaleRepository;
import br.com.amaral.service.SaleService;
import br.com.amaral.service.ServiceSendEmail;
import okhttp3.OkHttpClient;

@RestController
public class SaleController {

	@Autowired
	private ISaleRepository saleRepository;

	@Autowired
	private SaleService saleService;

	@Autowired
	private ServiceSendEmail serviceSendEmail;
	

	@ResponseBody
	@PostMapping(value = "**/create-sale")
	public ResponseEntity<Sale> createSale(@RequestBody @Valid Sale sale) {

		for (int i = 0; i < sale.getProductsSold().size(); i++) {
			sale.getProductsSold().get(i).setSale(sale);
		}

		saleRepository.saveAndFlush(sale);
		issueInvoice(sale);
		sendOrderConfirmation(sale);
		notifySupplierAboutOrder(sale);

		return new ResponseEntity<>(sale, HttpStatus.OK);
	}

	@ResponseBody
	@PutMapping(value = "**/activate-sales-record/{id}")
	public ResponseEntity<String> activateSalesRecord(@PathVariable(value = "id") Long id) throws ExceptionProject {

		existsInDAO(id);

		saleService.activateSalesRecord(id);
		return new ResponseEntity<>("OK: Activation completed successfully.", HttpStatus.OK);
	}

	@ResponseBody
	@DeleteMapping(value = "**/delete-sale-dao/{id}")
	public ResponseEntity<String> deleteSaleDAO(@PathVariable(value = "id") Long id) throws ExceptionProject {

		existsInDAO(id);

		saleService.deleteSaleDAO(id);
		return new ResponseEntity<>("OK: Deletion completed successfully.", HttpStatus.OK);
	}

	@ResponseBody
	@DeleteMapping(value = "**/delete-sale/{id}")
	public ResponseEntity<String> deleteSale(@PathVariable(value = "id") Long id) throws ExceptionProject {

		isActive(id);

		saleService.deleteSale(id);
		return new ResponseEntity<>("OK: Deletion completed successfully.", HttpStatus.OK);
	}

	@ResponseBody
	@GetMapping(value = "**/get-sale/{id}")
	public ResponseEntity<SaleDTO> getSale(@PathVariable("id") Long id) throws ExceptionProject {

		Sale sale = isActive(id);

		SaleDTO saleDTO = new SaleDTO();
		saleDTO.setTotalAmount(sale.getTotalAmount());
		saleDTO.setIndividual(sale.getIndividual());
		saleDTO.setDiscount(sale.getDiscount());
		saleDTO.setId(sale.getId());

		for (ProductSold productSold : sale.getProductsSold()) {

			ProductSoldDTO productDTO = new ProductSoldDTO();
			productDTO.setQuantity(productSold.getQuantity());
			productDTO.setProduct(productSold.getProduct());

			saleDTO.getProductsSoldDTO().add(productDTO);
		}

		return new ResponseEntity<>(saleDTO, HttpStatus.OK);
	}

	private void issueInvoice(Sale sale) {

	}

	/**
	 * Link https://docs.melhorenvio.com.br/docs
	 * 
	 * @param melhorEnvioDTO
	 * @return
	 * @throws Exception
	 */
	@ResponseBody
	@PostMapping(value = "**/get-shipping-data")
	public ResponseEntity<List<MelhorEnvioShippingCompanyDTO>> getShippingData(
			@RequestBody @Valid MelhorEnvioShippingDTO quoteShippingDTO) throws Exception {

		ObjectMapper objectMapper = new ObjectMapper();
		String json = objectMapper.writeValueAsString(quoteShippingDTO);

		OkHttpClient client = new OkHttpClient().newBuilder().build();
		okhttp3.MediaType mediaType = okhttp3.MediaType.parse("application/json");
		okhttp3.RequestBody body = okhttp3.RequestBody.create(mediaType, json);
		okhttp3.Request request = new okhttp3.Request.Builder()
				.url(APITokens.URL_MELHOR_ENVIO_SAND_BOX + "api/v2/me/shipment/calculate").method("POST", body)
				.addHeader("Accept", "application/json").addHeader("Content-Type", "application/json")
				.addHeader("Authorization", "Bearer " + APITokens.TOKEN_MELHOR_ENVIO_SAND_BOX)
				.addHeader("User-Agent", "amaral_adm@hotmail.com").build();

		okhttp3.Response response = client.newCall(request).execute();

		JsonNode jsonNode = new ObjectMapper().readTree(response.body().string());

		Iterator<JsonNode> iterator = jsonNode.iterator();

		List<MelhorEnvioShippingCompanyDTO> list = new ArrayList<>();

		while (iterator.hasNext()) {
			JsonNode node = iterator.next();

			MelhorEnvioShippingCompanyDTO shippingCompanyDTO = new MelhorEnvioShippingCompanyDTO();

			if (node.get("id") != null) {
				shippingCompanyDTO.setId(node.get("id").asText());
			}

			if (node.get("name") != null) {
				shippingCompanyDTO.setName(node.get("name").asText());
			}

			if (node.get("price") != null) {
				shippingCompanyDTO.setPrice(node.get("price").asText());
			}

			if (node.get("company") != null) {
				shippingCompanyDTO.setCompany(node.get("company").get("name").asText());
				shippingCompanyDTO.setPicture(node.get("company").get("picture").asText());
			}

			if (shippingCompanyDTO.isOK()) {
				list.add(shippingCompanyDTO);
			}
		}

		return new ResponseEntity<>(list, HttpStatus.OK);

	}

	@ResponseBody
	@GetMapping(value = "**/get-tracking-number/{saleId}")
	public ResponseEntity<String> getTrackingNumber(@PathVariable Long saleId) throws ExceptionProject, IOException {

		Sale sale = existsInDAO(saleId);

		MelhorEnvioTrackingDataDTO trackingDataDTO = new MelhorEnvioTrackingDataDTO();

		trackingDataDTO.setService(sale.getShippingCompany());
		trackingDataDTO.setAgency("49");
		trackingDataDTO.getFrom().setName(sale.getLegalEntity().getName());
		trackingDataDTO.getFrom().setPhone(sale.getLegalEntity().getPhone());
		trackingDataDTO.getFrom().setEmail(sale.getLegalEntity().getEmail());
		trackingDataDTO.getFrom().setCompany_document(sale.getLegalEntity().getCnpj());
		trackingDataDTO.getFrom().setState_register(sale.getLegalEntity().getStateTaxID());
		trackingDataDTO.getFrom().setAddress(sale.getLegalEntity().getAddresses().get(0).getStreetAddress());
		trackingDataDTO.getFrom().setComplement(sale.getLegalEntity().getAddresses().get(0).getComplement());
		trackingDataDTO.getFrom().setNumber(sale.getLegalEntity().getAddresses().get(0).getNumber());
		trackingDataDTO.getFrom().setDistrict(sale.getLegalEntity().getAddresses().get(0).getDistrict());
		trackingDataDTO.getFrom().setCity(sale.getLegalEntity().getAddresses().get(0).getCity());
		trackingDataDTO.getFrom().setCountry_id(sale.getLegalEntity().getAddresses().get(0).getCountryId());
		trackingDataDTO.getFrom().setPostal_code(sale.getLegalEntity().getAddresses().get(0).getPostalCode());
		trackingDataDTO.getFrom().setNote("Null");

		trackingDataDTO.getTo().setName(sale.getIndividual().getName());
		trackingDataDTO.getTo().setPhone(sale.getIndividual().getPhone());
		trackingDataDTO.getTo().setEmail(sale.getIndividual().getEmail());
		trackingDataDTO.getTo().setDocument(sale.getIndividual().getCpf());
		trackingDataDTO.getTo().setAddress(sale.getIndividual().shippingAddress().getStreetAddress());
		trackingDataDTO.getTo().setComplement(sale.getIndividual().shippingAddress().getComplement());
		trackingDataDTO.getTo().setNumber(sale.getIndividual().shippingAddress().getNumber());
		trackingDataDTO.getTo().setDistrict(sale.getIndividual().shippingAddress().getDistrict());
		trackingDataDTO.getTo().setCity(sale.getIndividual().shippingAddress().getCity());
		trackingDataDTO.getTo().setState_abbr(sale.getIndividual().shippingAddress().getState());
		trackingDataDTO.getTo().setCountry_id(sale.getIndividual().shippingAddress().getCountryId());
		trackingDataDTO.getTo().setPostal_code(sale.getIndividual().shippingAddress().getPostalCode());
		trackingDataDTO.getTo().setNote("Null");

		List<MelhorEnvioTrackingDataProductsDTO> products = new ArrayList<>();

		for (ProductSold productSold : sale.getProductsSold()) {

			MelhorEnvioTrackingDataProductsDTO productDTO = new MelhorEnvioTrackingDataProductsDTO();

			productDTO.setName(productSold.getProduct().getName());
			productDTO.setQuantity(productSold.getQuantity().toString());
			productDTO.setUnitary_value("" + productSold.getProduct().getSellingPrice().doubleValue());

			products.add(productDTO);
		}

		trackingDataDTO.setProducts(products);

		List<MelhorEnvioTrackingDataVolumesDTO> volumes = new ArrayList<>();

		for (ProductSold productSold : sale.getProductsSold()) {

			MelhorEnvioTrackingDataVolumesDTO volumesDTO = new MelhorEnvioTrackingDataVolumesDTO();

			volumesDTO.setHeight(productSold.getProduct().getHeight().toString());
			volumesDTO.setLength(productSold.getProduct().getLength().toString());
			volumesDTO.setWeight(productSold.getProduct().getWeight().toString());
			volumesDTO.setWidth(productSold.getProduct().getWidth().toString());

			volumes.add(volumesDTO);
		}

		trackingDataDTO.setVolumes(volumes);

		trackingDataDTO.getOptions().setInsurance_value("" + sale.getTotalAmount().doubleValue());
		trackingDataDTO.getOptions().setReceipt(false);
		trackingDataDTO.getOptions().setOwn_hand(false);
		trackingDataDTO.getOptions().setReverse(false);
		trackingDataDTO.getOptions().setNon_commercial(false);
		trackingDataDTO.getOptions().getInvoice().setKey(sale.getSalesInvoice().getKey());
		trackingDataDTO.getOptions().setPlatform(sale.getLegalEntity().getTradeName());

		MelhorEnvioTrackingDataTagsDTO tagDTO = new MelhorEnvioTrackingDataTagsDTO();
		tagDTO.setTag("Order identification on the platform: " + sale.getId());
		tagDTO.setUrl(null);
		trackingDataDTO.getOptions().getTags().add(tagDTO);

		String sendJson = new ObjectMapper().writeValueAsString(trackingDataDTO);

		OkHttpClient client = new OkHttpClient().newBuilder().build();
		okhttp3.MediaType mediaType = okhttp3.MediaType.parse("application/json");
		okhttp3.RequestBody body = okhttp3.RequestBody.create(mediaType, sendJson);
		okhttp3.Request request = new okhttp3.Request.Builder()
				.url(APITokens.URL_MELHOR_ENVIO_SAND_BOX + "api/v2/me/cart").method("POST", body)
				.addHeader("Accept", "application/json").addHeader("Content-Type", "application/json")
				.addHeader("Authorization", "Bearer " + APITokens.TOKEN_MELHOR_ENVIO_SAND_BOX)
				.addHeader("User-Agent", "amaral_adm@hotmail.com").build();

		okhttp3.Response response = client.newCall(request).execute();

		String jsonResponse = response.body().string();
		if (jsonResponse.contains("error")) {
			throw new ExceptionProject(jsonResponse);
		}

		JsonNode jsonNode = new ObjectMapper().readTree(jsonResponse);

		Iterator<JsonNode> iterator = jsonNode.iterator();

		String trackingNumber = "";

		while (iterator.hasNext()) {
			JsonNode node = iterator.next();
			if (node.get("id") != null) {
				trackingNumber = node.get("id").asText();
			} else {
				trackingNumber = node.asText();
			}
			break;
		}

		// em caso de erro, usar jdbcTemplate
		saleRepository.updateTrackingNumber(trackingNumber, sale.getId());

		OkHttpClient clientCheckout = new OkHttpClient().newBuilder().build();
		okhttp3.MediaType mediaTypeCheckout = okhttp3.MediaType.parse("application/json");
		okhttp3.RequestBody bodyCheckout = okhttp3.RequestBody.create(mediaTypeCheckout,
				"{\n    \"orders\": [\n        \"" + trackingNumber + "\"\n    ]\n}");
		okhttp3.Request requestCheckout = new okhttp3.Request.Builder()
				.url(APITokens.URL_MELHOR_ENVIO_SAND_BOX + "api/v2/me/shipment/checkout").method("POST", bodyCheckout)
				.addHeader("Accept", "application/json").addHeader("Content-Type", "application/json")
				.addHeader("Authorization", "Bearer " + APITokens.TOKEN_MELHOR_ENVIO_SAND_BOX)
				.addHeader("User-Agent", "amaral_adm@hotmail.com").build();

		okhttp3.Response responseCheckout = clientCheckout.newCall(requestCheckout).execute();

		if (!responseCheckout.isSuccessful()) {
			return new ResponseEntity<>("Unable to purchase shipping service", HttpStatus.OK);
		}

		OkHttpClient clientGenerate = new OkHttpClient().newBuilder().build();
		okhttp3.MediaType mediaTypeGenerate = okhttp3.MediaType.parse("application/json");
		okhttp3.RequestBody bodyGenerate = okhttp3.RequestBody.create(mediaTypeGenerate,
				"{\n    \"orders\":[\n        \"" + trackingNumber + "\"\n    ]\n}");
		okhttp3.Request requestGenerate = new okhttp3.Request.Builder()
				.url(APITokens.URL_MELHOR_ENVIO_SAND_BOX + "api/v2/me/shipment/generate").method("POST", bodyGenerate)
				.addHeader("Accept", "application/json").addHeader("Content-Type", "application/json")
				.addHeader("Authorization", "Bearer " + APITokens.TOKEN_MELHOR_ENVIO_SAND_BOX)
				.addHeader("User-Agent", "amaral_adm@hotmail.com").build();

		okhttp3.Response responseGenerate = clientGenerate.newCall(requestGenerate).execute();

		if (!responseGenerate.isSuccessful()) {
			return new ResponseEntity<>("It is not possible to generate proof of shipping service", HttpStatus.OK);
		}

		OkHttpClient clientPrint = new OkHttpClient().newBuilder().build();
		okhttp3.MediaType mediaTypePrint = okhttp3.MediaType.parse("application/json");
		okhttp3.RequestBody bodyPrint = okhttp3.RequestBody.create(mediaTypePrint,
				"{\n    \"mode\": \"private\",\n    \"orders\": [\n        \"" + trackingNumber + "\"\n    ]\n}");
		okhttp3.Request requestPrint = new okhttp3.Request.Builder()
				.url(APITokens.URL_MELHOR_ENVIO_SAND_BOX + "api/v2/me/shipment/print").method("POST", bodyPrint)
				.addHeader("Accept", "application/json").addHeader("Content-Type", "application/json")
				.addHeader("Authorization", "Bearer " + APITokens.TOKEN_MELHOR_ENVIO_SAND_BOX)
				.addHeader("User-Agent", "amaral_adm@hotmail.com").build();

		okhttp3.Response responsePrint = clientPrint.newCall(requestPrint).execute();

		if (!responsePrint.isSuccessful()) {
			return new ResponseEntity<>("Unable to print shipping service receipt.", HttpStatus.OK);
		}

		String labelUrl = responsePrint.body().string();
		saleRepository.updateLabelURL(labelUrl, sale.getId());

		return new ResponseEntity<>("Sucess", HttpStatus.OK);

	}

	@ResponseBody
	@GetMapping(value = "**/cancel-shipment/{trackingNumber}/{reason_id}/{description}")
	public ResponseEntity<String> cancelShipment(@PathVariable String trackingNumber, @PathVariable String reason_id,
			@PathVariable String description) throws IOException {

		OkHttpClient client = new OkHttpClient();
		okhttp3.MediaType mediaType = okhttp3.MediaType.parse("application/json");
		okhttp3.RequestBody body = okhttp3.RequestBody.create(mediaType,
				"{\n    \"order\": {\n        \"id\": \"" + trackingNumber + "\",\n        \"reason_id\": \""
						+ reason_id + "\",\n        \"description\": \"" + description + "\"\n    }\n}");
		okhttp3.Request request = new okhttp3.Request.Builder()
				.url(APITokens.URL_MELHOR_ENVIO_SAND_BOX + "api/v2/me/shipment/cancel").post(body)
				.addHeader("Accept", "application/json").addHeader("Content-Type", "application/json")
				.addHeader("Authorization", "Bearer " + APITokens.TOKEN_MELHOR_ENVIO_SAND_BOX)
				.addHeader("User-Agent", "amaral_adm@hotmail.com").build();

		okhttp3.Response response = client.newCall(request).execute();

		return new ResponseEntity<>(response.body().string(), HttpStatus.OK);
	}

	@ResponseBody
	@GetMapping(value = "**/dynamic-sales-query/{value}/{queryType}")
	public ResponseEntity<List<SaleDTO>> dynamicSalesQuery(@PathVariable("value") String value,
			@PathVariable("queryType") String queryType) {

		List<Sale> sale = null;

		if (queryType.equalsIgnoreCase("ByProduct")) {
			sale = saleRepository.findSaleByProduct(Long.parseLong(value));
		} else if (queryType.equalsIgnoreCase("ByPorductName")) {
			sale = saleRepository.findSaleByProductName(value.toUpperCase().trim());
		} else if (queryType.equalsIgnoreCase("ByCustomer")) {
			sale = saleRepository.findSaleByPersonId(Long.parseLong(value));
		} else if (queryType.equalsIgnoreCase("ByCustomerName")) {
			sale = saleRepository.findSaleByPersonName(value.toUpperCase().trim());
		} else if (queryType.equalsIgnoreCase("ByCpf")) {
			sale = saleRepository.findSaleByCpf(value.toUpperCase().trim());
		}

		if (sale == null) {
			sale = new ArrayList<>();
		}

		List<SaleDTO> saleDTOList = new ArrayList<>();

		for (Sale vcl : sale) {

			SaleDTO saleDTO = new SaleDTO();
			saleDTO.setTotalAmount(vcl.getTotalAmount());
			saleDTO.setIndividual(vcl.getIndividual());
			saleDTO.setDiscount(vcl.getDiscount());
			saleDTO.setId(vcl.getId());

			for (ProductSold productSold : vcl.getProductsSold()) {

				ProductSoldDTO productDTO = new ProductSoldDTO();
				productDTO.setQuantity(productSold.getQuantity());
				productDTO.setProduct(productSold.getProduct());

				saleDTO.getProductsSoldDTO().add(productDTO);
			}
		}

		return new ResponseEntity<>(saleDTOList, HttpStatus.OK);
	}

	@ResponseBody
	@GetMapping(value = "**/dynamic-sale-by-period-query/{startDate}/{endDate}")
	public ResponseEntity<List<SaleDTO>> dynamicSaleByPeriodQuery(@PathVariable("startDate") String startDate,
			@PathVariable("endDate") String endDate) {

		List<Sale> sale = null;

		saleService.saleByPeriodQuery(startDate, endDate);

		if (sale == null) {
			sale = new ArrayList<>();
		}

		List<SaleDTO> saleDTOList = new ArrayList<>();

		for (Sale vcl : sale) {

			SaleDTO saleDTO = new SaleDTO();
			saleDTO.setTotalAmount(vcl.getTotalAmount());
			saleDTO.setIndividual(vcl.getIndividual());
			saleDTO.setDiscount(vcl.getDiscount());
			saleDTO.setId(vcl.getId());

			for (ProductSold productSold : vcl.getProductsSold()) {

				ProductSoldDTO productDTO = new ProductSoldDTO();
				productDTO.setQuantity(productSold.getQuantity());
				productDTO.setProduct(productSold.getProduct());

				saleDTO.getProductsSoldDTO().add(productDTO);
			}
		}

		return new ResponseEntity<>(saleDTOList, HttpStatus.OK);
	}

	private void sendOrderConfirmation(Sale sale) {

		StringBuilder message = new StringBuilder();

		message.append("<html><body>");
		message.append("<p>Hello, ").append(sale.getIndividual().getName()).append(",</p>");
		message.append("<p>Thank you for choosing us! Your order has been successfully placed.</p>");
		message.append("<p>Order Details:</p>");
		message.append("<ul>");

		for (ProductSold product : sale.getProductsSold()) {
			message.append("<li>").append(product.getProduct().getName()).append(" - Quantity: ")
					.append(product.getQuantity()).append("</li>");
		}

		message.append("</ul>");
		message.append("<p>Total Amount: $").append(sale.getTotalAmount()).append("</p>");
		message.append("<p>Shipping Address:</p>");
		message.append("<p>").append(sale.getIndividual().shippingAddress()).append("</p>");
		message.append(
				"<p>We will notify you once your order is shipped. If you have any questions, feel free to contact us.</p>");
		message.append("<p>Thank you for shopping with us!</p>");
		message.append("</body></html>");

		try {
			serviceSendEmail.sendEmailHtml("Order Confirmation", message.toString(), sale.getIndividual().getEmail());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void notifySupplierAboutOrder(Sale sale) {

		StringBuilder message = new StringBuilder();

		message.append("<html><body>");
		message.append("<p>Hello, ").append(sale.getLegalEntity().getName()).append(",</p>");
		message.append("<p>New Order Received!</p>");
		message.append("<p>Order Details:</p>");
		message.append("<ul>");

		for (ProductSold product : sale.getProductsSold()) {
			message.append("<li>").append(product.getProduct().getName()).append(" - Quantity: ")
					.append(product.getQuantity()).append("</li>");
		}

		message.append("</ul>");
		message.append("<p>Total Amount: $").append(sale.getTotalAmount()).append("</p>");
		message.append("<p>Customer Information:</p>");
		message.append("<p>Name: ").append(sale.getIndividual().getName()).append("</p>");
		message.append("<p>Email: ").append(sale.getIndividual().getEmail()).append("</p>");
		message.append("<p>Shipping Address:</p>");
		message.append("<p>").append(sale.getIndividual().shippingAddress()).append("</p>");
		message.append("<p>Please fulfill this order as soon as possible. Contact the customer if needed.</p>");
		message.append("<p>Thank you for your prompt attention!</p>");
		message.append("</body></html>");

		try {
			serviceSendEmail.sendEmailHtml("New Order Received", message.toString(), sale.getLegalEntity().getEmail());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private Sale existsInDAO(Long id) throws ExceptionProject {

		Sale sale = saleRepository.findById(id).orElse(null);

		if (sale == null) {
			throw new ExceptionProject("Operation not performed: Not included in the ID database: " + id);
		}

		return sale;
	}

	private Sale isActive(Long id) throws ExceptionProject {

		Sale sale = saleRepository.getActiveSaleById(id);

		if (sale == null) {
			throw new ExceptionProject(
					"Operation not carried out: It is not included in the Active ID database: " + id);
		}

		return sale;
	}

}
