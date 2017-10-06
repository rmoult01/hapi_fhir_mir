package ca.uhn.fhir.jpa.demo;

import ca.uhn.fhir.rest.server.interceptor.InterceptorAdapter;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import jdk.nashorn.internal.runtime.GlobalConstants;
import org.apache.commons.io.IOUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class WadoRsInterceptor extends InterceptorAdapter {

	static final String WADO_SERVER_URL = "http://localhost:8080/dcm4chee-arc/aets/DCM4CHEE/rs";

	static final String DCM_TAG_PATIENT_ID = "00100020";

	public WadoRsInterceptor() {
		super();
	}

	@Override
	public boolean incomingRequestPreProcessed(HttpServletRequest theRequest, HttpServletResponse theResponse) {

		// Catch the /studies request
		String url = theRequest.getRequestURL().toString();
		String cp = theRequest.getContextPath().toLowerCase();
		String sp = theRequest.getServletPath();
		if (url.contains(cp + sp + "/studies/") == false) return true;


		String pid = getStudyPid(cp + sp, theRequest);

		/*
		 * This is where we would need to validate the pid before continuing with the retrieval.
		 */

		// This is the forward
		forwardRequest(cp + sp, theRequest, theResponse);


		return false;
	}

	/**
	 * WADO Query to retrieve patient MRN corresponding to study.
	 * @param prefix original query context path and servlet path, concatenated.
	 * @param req original request.
	 * @return Medical Records number of patient from study, or null. Note:
	 * at this time exceptions are caught and printed, but ignored. This
	 * won't do later on.
	 */
	private String getStudyPid (String prefix,  HttpServletRequest req) {
		try {
			prefix += "/studies/";
			String cmd = req.getRequestURI().substring(prefix.length());
			if (cmd.indexOf("?") > 0) cmd = cmd.substring(0, cmd.indexOf("?"));

			List<Map<String, List<String>>> studies = Utl.wadoQuery(cmd, null);
			if (studies.isEmpty())
				throw new Exception("study not found");
			return studies.get(0).get(DCM_TAG_PATIENT_ID).get(0).toString();

		} catch (Exception e) {
			e.printStackTrace();
			// pass
		}
		return null;
	}

	/**
	 * Forwards a "studies" request to WADO RS and returns result.
	 * @param prefix the string in the url immediately preceding "/studies/"
	 * @param req the HttpServletRequest
	 * @param resp the HttpServletResponse
	 */
	private void forwardRequest(String prefix, HttpServletRequest req, HttpServletResponse resp) {
		String method = req.getMethod();
		final boolean hasoutbody = (method.equals("POST"));

		try {
			String cmd = req.getRequestURI().substring(prefix.length());
			final URL url = new URL(WADO_SERVER_URL  // no trailing slash
				+ cmd
				+ (req.getQueryString() != null ? "?" + req.getQueryString() : ""));
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod(method);

			final Enumeration<String> headers = req.getHeaderNames();
			while (headers.hasMoreElements()) {
				final String header = headers.nextElement();
				final Enumeration<String> values = req.getHeaders(header);
				while (values.hasMoreElements()) {
					final String value = values.nextElement();
					conn.addRequestProperty(header, value);
				}
			}

			conn.setUseCaches(false);
			conn.setDoInput(true);
			conn.setDoOutput(hasoutbody);
			conn.connect();

			final byte[] buffer = new byte[16384];
			while (hasoutbody) {
				final int read = req.getInputStream().read(buffer);
				if (read <= 0) break;
				conn.getOutputStream().write(buffer, 0, read);
			}

			resp.setStatus(conn.getResponseCode());
			for (int i = 0; ; ++i) {
				final String header = conn.getHeaderFieldKey(i);
				if (header == null) break;
				final String value = conn.getHeaderField(i);
				resp.setHeader(header, value);
			}

			while (true) {
				final int read = conn.getInputStream().read(buffer);
				if (read <= 0) break;
				resp.getOutputStream().write(buffer, 0, read);
			}
		} catch (Exception e) {
			e.printStackTrace();
			// pass
		}
	}
}
