package ca.uhn.fhir.jpa.demo;

/*
 * common items for interceptors in ERL development.
 * Implement this in ERL classes needing these items.
 */
public interface Cmn {

	static final String DCM_TAG_CHARACTER_SET = "00080005";
	static final String DCM_TAG_STUDY_DATE = "00080020";
	static final String DCM_TAG_STUDY_TIME = "00080030";
	static final String DCM_TAG_ACCESSION = "00080050";
	static final String DCM_TAG_RETRIEVE_AE_TITLE = "00080054";
	static final String DCM_TAG_INSTANCE_AVAILABILITY = "00080056";
	static final String DCM_TAG_MODALITIES = "00080061";
	static final String DCM_TAG_REF_PHYS = "00080090";
	static final String DCM_TAG_RETRIEVE_URL = "00081190";
	static final String DCM_TAG_PATIENT_NAME = "00100010";
	static final String DCM_TAG_PATIENT_ID = "00100020";
	static final String DCM_TAG_PATIENT_DOB = "00100030";
	static final String DCM_TAG_PATIENT_SEX = "00100040";
	static final String DCM_TAG_STUDY_UID = "0020000D";
	static final String DCM_TAG_STUDY_ID = "00200010";
	static final String DCM_TAG_NUM_SERIES = "00201206";
	static final String DCM_TAG_NUM_INSTANCES = "00201208";
}
