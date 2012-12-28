package org.apache.shindig.auth;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.shindig.common.crypto.BasicBlobCrypter;
import org.apache.shindig.common.crypto.BlobCrypter;
import org.apache.shindig.common.crypto.BlobCrypterException;

public class ShindigTokenCrypter {
	public static final String EMBEDDED_KEY_PREFIX = "embedded:";
	public static final String CLASSPATH_KEY_PREFIX = "classpath:";

	private static final String CONTAINER = "default";
	private static final String DOMAIN = "default";

	private final String gadgetOwner;
	private final String gadgetViewer;
	private final String widgetId;
	private final String appUrl;
	

	public String getAppUrl() {
		return appUrl;
	}

	private final String encryptedToken;
	private final BlobCrypter blobCrypter;

	private static BlobCrypter getBlobCrypter() throws IOException {

		BlobCrypter crypter = new BasicBlobCrypter("edia_opensocial_demo");
		return crypter;
	}

	public ShindigTokenCrypter(final String gadgetOwner, final String gadgetViewer,
			final String widgetId, final String appUrl) throws IOException, BlobCrypterException {
		this.blobCrypter = getBlobCrypter();
		this.gadgetOwner = gadgetOwner;
		this.gadgetViewer = gadgetViewer;
		this.widgetId = widgetId;
		this.appUrl = appUrl;

		Map<String, String> values = new HashMap<String, String>();
		values.put(AbstractSecurityToken.Keys.MODULE_ID.getKey(), widgetId);
		values.put(AbstractSecurityToken.Keys.APP_URL.getKey(), appUrl);
		
		values.put(AbstractSecurityToken.Keys.OWNER.getKey(), gadgetOwner);
		values.put(AbstractSecurityToken.Keys.VIEWER.getKey(), gadgetViewer);
		values.put(AbstractSecurityToken.Keys.TRUSTED_JSON.getKey(), "");

		BlobCrypterSecurityToken securityToken = new BlobCrypterSecurityToken(
				CONTAINER, DOMAIN, null, values);

		encryptedToken = blobCrypter.wrap(securityToken.toMap());
	}

	public ShindigTokenCrypter(final String encryptedToken) throws IOException,
			BlobCrypterException {
		this.blobCrypter = getBlobCrypter();

		
		this.encryptedToken = encryptedToken;

		Map<String, String> values = blobCrypter.unwrap(encryptedToken);

		this.gadgetOwner = values
				.get(AbstractSecurityToken.Keys.OWNER.getKey());
		this.gadgetViewer = values.get(AbstractSecurityToken.Keys.VIEWER
				.getKey());
		this.widgetId = values.get(AbstractSecurityToken.Keys.MODULE_ID.getKey());
		this.appUrl = values.get(AbstractSecurityToken.Keys.APP_URL.getKey());

	}

	public String getEncryptedTokenForUrl() {
		return CONTAINER + ":" + encryptedToken;
	}

	public String getEncryptedToken() {
		return encryptedToken;
	}

	public String getGadgetOwner() {
		return gadgetOwner;
	}

	public String getGadgetViewer() {
		return gadgetViewer;
	}

	public String getWidgetId() {
		return widgetId;
	}
	
	public static void main(String a[]) throws IOException,
			BlobCrypterException {
		String encryptedToken = new ShindigTokenCrypter("bert1", "bert1", "1156565651", "http://www.testgadgetblabla.nl")
				.getEncryptedToken();
		System.out.println(encryptedToken);

		System.out.println(new ShindigTokenCrypter(encryptedToken).getGadgetOwner());

	}
}
