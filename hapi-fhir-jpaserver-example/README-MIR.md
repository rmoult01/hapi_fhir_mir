## Running modified server for Image processing testing
Note: The port assignments shown here **MUST** be used, as the 
modified example server is a prototype only; some things are
hard coded.
### Set up a dcm4chee server for testing

A dockerized dcm4chee is used for testing. Setting it up 
involves three containers. I found that running each container
in a separate terminal window and leaving the "-d" parameter off
allowed me to see logs readily.

1. dcm4che/slapd-dcm4chee

Ref: https://hub.docker.com/r/dcm4che/slapd-dcm4chee/

docker run --name slapd \
   -p 389:389 \
   -v /etc/localtime:/etc/localtime \
   -v /var/local/dcm4chee-arc/ldap:/var/lib/ldap \
   -v /var/local/dcm4chee-arc/slapd.d:/etc/ldap/slapd.d \
   -d dcm4che/slapd-dcm4chee:2.4.44-10.5
   
2. dcm4che/postgres-dcm4chee

Ref: https://hub.docker.com/r/dcm4che/postgres-dcm4chee/

docker run --name postgres \
   -p 5432:5432 \
   -v /etc/localtime:/etc/localtime \
   -e POSTGRES_DB=pacsdb \
   -e POSTGRES_USER=pacs\
   -e POSTGRES_PASSWORD=pacs \
   -v /var/local/dcm4chee-arc/db:/var/lib/postgresql/data \
   -d dcm4che/postgres-dcm4chee:9.6-10
   
3. dcm4che/dcm4chee-arc-psql

Ref: https://hub.docker.com/r/dcm4che/dcm4chee-arc-psql/

I used the non-secured version, tag 5.10.5, 
ignored the option to use Elasticsearch,
and moved the Wildfly port to 9090.

docker run --name dcm4chee-arc \
   -p 9090:8080 \
   -p 9990:9990 \
   -p 11112:11112 \
   -p 2575:2575 \
   -v /var/local/dcm4chee-arc/wildfly:/opt/wildfly/standalone \
   -v /var/local/dcm4chee-arc/storage:/storage \
   --link slapd:ldap \
   --link postgres:db \
   dcm4che/dcm4chee-arc-psql:5.10.5
   
The reference page gives more details, but this setup provides
a dcm4chee server with:

- DICOM_HOST = localhost
- DICOM_PORT = 11112
- AE_TITLE = DCM4CHEE
- HL7_PORT = 2575
- Wildfly Administration Console: http://localhost:9990, 
User: admin, pw: admin
- WADO-RS  Base URL: http://localhost:9090/dcm4chee-arc/aets/DCM4CHEE/rs

### Load test images into dcm4chee server

I loaded some anonymized test images in the dcm4chee server.
I copied them into the directory 'testImages' in this project
if you would like to use them. I used the CTN utility send_image
from the directory the images were in:

send_image -c DCM4CHEE -Z localhost 11112 *

This loaded a study with 1 series and six images:

- (0x0008,0x0050) AccessionNumber = CASCB1021
- (0x0008,0x0020) StudyDate = 20150202
- (0x0008,0x0030) StudyTime = 154122.413000
- (0x0010,0x0030) PatientBirthDate = 19780202
- (0x0010,0x0020) PatientID = IIG-DEPT1021
- (0x0012,0x0062) PatientIdentityRemoved = YES
- (0x0010,0x0010) PatientName = Beta^B
- (0x0020,0x000d) StudyInstanceUID = 1.3.6.1.4.1.21367.201599.1.201606010958044

### run the dockerized example server

I run the modified example server from Intellij on a tomcat server instance.

You can run a dockerized version:

docker pull rmoult01/imageserver

docker run -p 8080:8080 rmoult01/imageserver

You will be able to access the server's built in client at

http://localhost:8080

### Create sample Patient resource in example server

I created this patient for testing, inserting the MRN that was in the
dicom images I loaded. Note the ID the server gives the resource so you
can use it for testing:

{
  "resourceType": "Patient",
  "identifier": [
    {
      "use": "usual",
      "system": "http://www.goodhealth.org/identifiers/mrn",
      "value": "123456"
    },
{
            "use":"usual",
            "type":{
                "coding":[
                    {
                        "system":"http://hl7.org/fhir/v2/0203",
                        "code":"MR",
                        "display":"Medical record number"
                    }
                ],
                "text":"Medical record number"
            },
            "system":"http://hospital.smarthealthit.org",
            "value":"IIG-DEPT1021"
        }
  ],
  "name": [
    {
      "family": [
        "Levin"
      ],
      "given": [
        "Henry"
      ],
      "suffix": [
        "The 7th"
      ]
    }
  ],
  "gender": "male",
  "birthDate": "1932-09-24",
  "active": true
}

### Testing the modifications to the example server

Some of the tests can be run directly from the built in hapi client.
For others you will need an http client that can handle restful 
service calls. I just installed "RESTClient" in my firefox. There are 
addons available for chrome also, or you can use curl.

- Test ImageStudy query 
http://localhost:8080/baseDstu3/ImagingStudy?Patient=34952

- Test WADO Study request
http://localhost:8080/baseDstu3/studies/1.3.6.1.4.1.21367.201599.1.201606010958044





