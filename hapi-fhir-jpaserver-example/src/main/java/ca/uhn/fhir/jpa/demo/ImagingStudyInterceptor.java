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
import org.hl7.fhir.dstu3.model.*;


import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.StringWriter;
import java.net.HttpURLConnection;
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
		String url = theRequestDetails.getCompleteUrl();

		Map<String, String[]> fhirParams = theRequestDetails.getParameters();

		String mrn = null;
		String lu = null;
		String patientReferenceStr = null;

		for (String key : fhirParams.keySet()) {
			String[] value = fhirParams.get(key);
			if (key.equalsIgnoreCase("Patient")) {
				mrn = Utl.getPatientMrn(value[0]);
				continue;
			}
			if (key.equalsIgnoreCase("_lastUpdated")) {
				lu = value[0];
			}
		}

		if (mrn == null)
			throw new InvalidRequestException("Required parameter 'patient' not found.");

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
}
