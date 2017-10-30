package ca.uhn.fhir.jpa.demo;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.rest.method.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;

import ca.uhn.fhir.rest.server.interceptor.InterceptorAdapter;
import com.google.gson.*;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.dstu3.model.*;


import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

public class ImagingStudyInterceptor extends InterceptorAdapter implements Cmn {

	public ImagingStudyInterceptor() {
		super();
	}

	@Override
	public boolean incomingRequestPostProcessed(RequestDetails theRequestDetails,
															  HttpServletRequest theRequest, HttpServletResponse theResponse)
		throws AuthenticationException {

		try {

		// ImagingStudy searches.
		String rn = theRequestDetails.getResourceName();
		if (rn == null || rn.equals("ImagingStudy") == false) return true;
		RequestTypeEnum rt = theRequestDetails.getRequestType();
		if (rt == null || rt != RequestTypeEnum.GET) return true;
		RestOperationTypeEnum ot = theRequestDetails.getRestOperationType();
		if (ot == null || ot != RestOperationTypeEnum.SEARCH_TYPE) return true;
		System.out.println("ImageStudy intercepted");

		authenticate(theRequestDetails, theRequest, theResponse);

		String url = theRequestDetails.getCompleteUrl();

		Map<String, String[]> fhirParams = theRequestDetails.getParameters();

		String pid = null;
		String mrn = null;
		String lu = null;
		String patientReferenceStr = null;

		for (String key : fhirParams.keySet()) {
			String[] value = fhirParams.get(key);
			if (key.equalsIgnoreCase("patient")) {
				pid = value[0];
				continue;
			}
			if (key.equalsIgnoreCase("_lastUpdated")) {
				lu = value[0];
			}
		}

		if (pid == null)
			throw new InvalidRequestException("Required parameter 'patient' not found.");

		mrn = Utl.getPatientMrn(pid);

		String body = wadoQuery(mrn, lu, patientReferenceStr, url);
		theResponse.addHeader("Content-Type", "application/fhir+json");
		try {
			theResponse.getWriter().write(body);
		} catch (IOException ioe) {
			ioe.printStackTrace();
			String em = "Error writing httpresponse body " + ioe.getMessage();
			throw new InternalErrorException(em, ioe);
		}

		return false;

		} catch (Exception e) {
			e.printStackTrace();
			String em = "Error processing image request " + e.getMessage();
			throw new InternalErrorException(em, e);
		}
	}

	/**
	 * Processes an ImageStudy query by FHIR Patient by forwarding it as a WADO RS query by PatientID
	 * @param mrn Patient Medical Record Number
	 * @param lu last updated date. null for all studies, yyyyMMddhhMMss (or some prefix) to exclude studies before then.
	 * @param patientReferenceStr The FHIR Patient reference string, for example, Patient/1234
	 * @param queryUrl The original FHIR ImageStudy query (gets put in the Bundle resource).
	 * @return A Bundle containing 0 or more ImageStudy resources, or an error resource.
	 */
	private String wadoQuery(String mrn, String lu, String patientReferenceStr, String queryUrl) {
		String cmd = null;

		try {
			List<Map<String, List<String>>> dcmCodeMaps = Utl.wadoQuery("/studies?PatientID=" + mrn, lu);
			List<ImagingStudy> studies = new ArrayList<ImagingStudy>();

			for (Map<String, List<String>> dcmCodeMap : dcmCodeMaps) {
				// These entries may need 'fleshing out', for example code set UIDs.

				ImagingStudy study = new ImagingStudy();
				study.setPatient(new Reference(patientReferenceStr));

				String s = dcmCodeMap.get(DCM_TAG_STUDY_UID).get(0);
				if (isThere(s))
					study.setUidElement(new OidType("urn:oid:" + s));

				s = dcmCodeMap.get(DCM_TAG_ACCESSION).get(0);
				if (isThere(s))
					study.setAccession(new Identifier().setValue(s));

				s = dcmCodeMap.get(DCM_TAG_STUDY_ID).get(0);
				if (isThere(s))
					study.addIdentifier(new Identifier().setValue(s));

				s = dcmCodeMap.get(DCM_TAG_INSTANCE_AVAILABILITY).get(0);
				if (isThere(s))
					study.setAvailability(ImagingStudy.InstanceAvailability.fromCode(s));

				List<String> sl = dcmCodeMap.get(DCM_TAG_MODALITIES);
				for (String l : sl) {
					if (isThere(l))
						study.addModalityList(new Coding().setCode(l));
				}

				s = dcmCodeMap.get(DCM_TAG_REF_PHYS).get(0);
				if (isThere(s))
					study.setReferrer(new Reference().setDisplay(s));

				s = dcmCodeMap.get(DCM_TAG_RETRIEVE_URL).get(0);
				if (isThere(s))
					study.addEndpoint(new Reference().setReference(s));

				s = dcmCodeMap.get(DCM_TAG_NUM_SERIES).get(0);
				if (isThere(s))
					study.setNumberOfSeries(Integer.parseInt(s));

				s = dcmCodeMap.get(DCM_TAG_NUM_INSTANCES).get(0);
				if (isThere(s))
					study.setNumberOfInstances(Integer.parseInt(s));

				String d = dcmCodeMap.get(DCM_TAG_STUDY_DATE).get(0);
				String t = dcmCodeMap.get(DCM_TAG_STUDY_TIME).get(0);
				t = t.substring(0, t.indexOf("."));
				if (d.length() == 8) {
					String fmt = "yyyyMMdd";
					if (t.length() == 6) {
						fmt += "HHmmss";
						d += t;
					}
					SimpleDateFormat sdf = new SimpleDateFormat(fmt);
					Date sd = null;
					try {
						sd = sdf.parse(d);
					} catch (Exception e) {
					}
					;
					if (sd != null)
						study.setStarted(sd);
				}

				studies.add(study);

			} // pass json entries (studies)

			Bundle bundle = new Bundle();
			bundle.setId(UUID.randomUUID().toString());
			bundle.addLink(new Bundle.BundleLinkComponent().setRelation("self").setUrl(queryUrl));
			bundle.setType(Bundle.BundleType.SEARCHSET);
			bundle.setTotal(studies.size());

			for (ImagingStudy study : studies) {
				Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();
				entry.setResource(study);
				bundle.addEntry(entry);
			}

			FhirContext ctx = FhirContext.forDstu3();
			IParser parser = ctx.newJsonParser();
			String body = parser.encodeResourceToString(bundle);

			return body;


		} catch (Exception e) {
			e.printStackTrace();
			String em = "Error processing image request " + e.getMessage() + " on command " + cmd;
			throw new InternalErrorException(em, e);
		}
	}

	private boolean isThere(String val) {
		return val != null && val.isEmpty() == false;
	}

	// properties for authentication

	private static final boolean AUTHENTICATION_ENABLED = true;
	private static final String  INTROSPECTION_SERVICE_URL = "http://localhost:9004/api/introspect";

	private void authenticate(RequestDetails theRequestDetails,
									  HttpServletRequest theRequest, HttpServletResponse theResponse)
		throws AuthenticationException, InvalidRequestException {

		if (!AUTHENTICATION_ENABLED) return;

		// Get, validate pid parameter
		Map<String, String[]> requestParameters = theRequestDetails.getParameters();
		if (requestParameters == null) throw new AuthenticationException("required parameter missing");
		if (requestParameters.containsKey("patient") == false)
			throw new AuthenticationException("required parameter 'patient' missing");
		String pid = StringUtils.trimToEmpty(requestParameters.get("patient")[0]);
		if (pid.isEmpty()) throw new AuthenticationException("required parameter patient empty");

		// Get, validate authorization token
		List<String> authHeaderFields = theRequestDetails.getHeaders("Authorization");
		if (authHeaderFields == null) throw new AuthenticationException("required header 'Authorization' missing");
		if (authHeaderFields.size() != 1) throw new AuthenticationException("Authorization header invalid format");
		String authTokenType = StringUtils.trimToEmpty(authHeaderFields.get(0));
		if (StringUtils.startsWithIgnoreCase(authTokenType, "Bearer ") == false)
			throw new AuthenticationException("Invalid Authorization token type");
		String authToken = StringUtils.trimToEmpty(authTokenType.substring(6));
		if (authToken.isEmpty()) throw new AuthenticationException("Authorization token missing");

		// Query token instrospection service
		URL url = null;
		try {
			url = new URL(INTROSPECTION_SERVICE_URL);
			String postData = "token=" + authToken + "&patient=" + pid;
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setUseCaches(false);
			conn.setDoInput(true);
			conn.setDoOutput(true);
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			conn.setRequestProperty("Content-Length", "" + postData.getBytes().length);
			conn.setRequestProperty("Content-Language", "en-US");
			OutputStream os = conn.getOutputStream();
			os.write(postData.getBytes("UTF-8"));
			os.close();
			conn.connect();

		if (conn.getResponseCode() != 200) throw new AuthenticationException("Authorization failed");

			Map<String, List<String>> responseHeaders = conn.getHeaderFields();
			List<String> responseContentTypes = responseHeaders.get("Content-Type");
			if (responseContentTypes == null)
				throw new AuthenticationException("Required header missing, 'Content-Type'");
			if (responseContentTypes.size() != 1)
				throw new AuthenticationException("Required header invalid, 'Content-Type'");
			String responseContentType = responseContentTypes.get(0);
			if (responseContentType == null)
				throw new AuthenticationException("Required header empty, 'Content-Type'");
			if (responseContentType.contains("json") == false)
				throw new AuthenticationException("Content-Type " + responseContentType + " not supported");

			StringWriter writer = new StringWriter();
			IOUtils.copy(conn.getInputStream(), writer, "UTF-8");
			String responseBody = writer.toString();
			if (responseBody == null || responseBody.isEmpty())
				throw new AuthenticationException("Response body empty");

			JsonObject responseJson = (JsonObject) new JsonParser().parse(responseBody);
			JsonElement scope = responseJson.get("scope");
			if (scope == null) throw new AuthenticationException("Authorization failed");
			String scopes = scope.getAsString();
			if (Utl.isScopeAuthorized("ImagingStudy", "read", scopes)) return;
			throw new AuthenticationException("Authorization failed");

		} catch (MalformedURLException e ) {
		throw new AuthenticationException(e.getMessage());
	} catch (IOException io) {
		throw new AuthenticationException(io.getMessage());
	}

	}
}
