package org.openmrs.module.fhir.mapper.emr;

import ca.uhn.fhir.model.dstu2.resource.Bundle;
import ca.uhn.fhir.model.dstu2.resource.Composition;
import ca.uhn.fhir.model.dstu2.resource.Encounter;
import org.junit.After;
import org.junit.Test;
import org.openmrs.api.PatientService;
import org.openmrs.module.fhir.MapperTestHelper;
import org.openmrs.module.fhir.mapper.model.ShrEncounterBundle;
import org.openmrs.module.fhir.utils.DateUtil;
import org.openmrs.module.fhir.utils.FHIRBundleHelper;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.openmrs.module.fhir.MapperTestHelper.getSystemProperties;

@org.springframework.test.context.ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class FHIREncounterMapperIntegrationTest extends BaseModuleWebContextSensitiveTest {

    @Autowired
    private ApplicationContext springContext;

    @Autowired
    private FHIREncounterMapper fhirEncounterMapper;

    @Autowired
    private PatientService patientService;

    public Bundle loadSampleFHIREncounter() throws Exception {
        return (Bundle) new MapperTestHelper().loadSampleFHIREncounter("encounterBundles/dstu2/testFHIREncounter.xml", springContext);
    }

    @After
    public void tearDown() throws Exception {
        deleteAllData();
    }

    @Test
    public void shouldMapFhirEncounter() throws Exception {
        executeDataSet("testDataSets/shrClientEncounterReverseSyncTestDS.xml");
        final Bundle encounterBundle = loadSampleFHIREncounter();
        assertEquals("Bundle/4fe6f9e2-d10a-4956-aae5-091e810090e1", encounterBundle.getId().getValue());

        FHIRBundleHelper.getComposition(encounterBundle);
        final Composition composition = FHIRBundleHelper.getComposition(encounterBundle);
        assertNotNull(composition);

        assertEquals(DateUtil.parseDate("2014-07-10T16:05:09+05:30"), composition.getDate());
        final Encounter encounter = FHIRBundleHelper.getEncounter(encounterBundle);
        assertNotNull(encounter);
        assertEquals("4d2f9872-4df1-438e-9d72-0a8b161d409b", encounter.getId().getIdPart());

        org.openmrs.Patient emrPatient = patientService.getPatient(1);
        ShrEncounterBundle encounterComposition = new ShrEncounterBundle(encounterBundle, "HIDA764177", "shr-enc-id-1");
        org.openmrs.Encounter emrEncounter = fhirEncounterMapper.map(emrPatient, encounterComposition, getSystemProperties("1"));

        assertNotNull(emrEncounter);
        assertEquals(emrPatient, emrEncounter.getPatient());
        assertEquals(DateUtil.parseDate("2014-07-10T16:05:09+05:30"), emrEncounter.getEncounterDatetime());
    }
}
