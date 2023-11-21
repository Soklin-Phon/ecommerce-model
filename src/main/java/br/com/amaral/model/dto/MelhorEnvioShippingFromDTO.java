package br.com.amaral.model.dto;

/**
 * MelhorEnvio API
 */
import java.io.Serializable;

public class MelhorEnvioShippingFromDTO implements Serializable {

private static final long serialVersionUID = 1L;
	
	
	private String postal_code;

	public String getPostal_code() {
		return postal_code;
	}

	public void setPostal_code(String postal_code) {
		this.postal_code = postal_code;
	}

}