package org.openmrs.module.shrclient.handlers;

import ca.uhn.fhir.model.dstu2.resource.Bundle;
import org.ict4h.atomfeed.client.domain.Event;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.openmrs.DrugOrder;
import org.openmrs.Encounter;
import org.openmrs.OrderType;
import org.openmrs.Patient;
import org.openmrs.User;
import org.openmrs.api.EncounterService;
import org.openmrs.module.fhir.Constants;
import org.openmrs.module.fhir.MRSProperties;
import org.openmrs.module.fhir.mapper.bundler.CompositionBundle;
import org.openmrs.module.shrclient.dao.IdMappingsRepository;
import org.openmrs.module.shrclient.identity.IdentityUnauthorizedException;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.util.PropertiesReader;
import org.openmrs.module.shrclient.util.SHRClient;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.openmrs.module.shrclient.util.SystemUserService;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.openmrs.module.fhir.utils.PropertyKeyConstants.*;

public class EncounterPushTest {

    private static final String HEALTH_ID = "1234567890123";
    private static final String patientUrl = "patients/" + HEALTH_ID + "/encounters";
    @Mock
    private EncounterService encounterService;

    @Mock
    private SHRClient shrClient;

    @Mock
    private PropertiesReader propertiesReader;

    @Mock
    private SystemUserService systemUserService;

    @Mock
    private CompositionBundle compositionBundle;

    @Mock
    private IdMappingsRepository idMappingsRepository;

    @Mock
    private ClientRegistry clientRegistry;

    private EncounterPush encounterPush;

    @Before
    public void setup() throws IdentityUnauthorizedException {
        initMocks(this);
        when(clientRegistry.getSHRClient()).thenReturn(shrClient);
        encounterPush = new EncounterPush(
                encounterService,
                propertiesReader, compositionBundle,
                idMappingsRepository,
                clientRegistry,
                systemUserService);
    }

    @Test
    public void shouldProcessEncounterCreateEvent() throws IOException {
        final String uuid = "123abc456";
        String facilityId = "10000069";

        final Event event = new Event("id100", "/openmrs/ws/rest/v1/encounter/" + uuid
                + "?v=custom:(uuid,encounterType,patient,visit,orders:(uuid,orderType,concept,voided))");
        org.openmrs.Encounter openMrsEncounter = getOpenMrsEncounter(uuid);
        final Bundle bundle = new Bundle();

        when(propertiesReader.getShrProperties()).thenReturn(getShrProperties(facilityId));

        when(encounterService.getEncounterByUuid(uuid)).thenReturn(openMrsEncounter);
        when(shrClient.post(anyString(), eq(bundle))).thenReturn("{\"encounterId\":\"shr-uuid\"}");
        when(compositionBundle.create(any(Encounter.class), eq(HEALTH_ID), any(SystemProperties.class))).thenReturn(bundle);
        when(idMappingsRepository.findByExternalId(facilityId)).thenReturn(null);
        IdMapping patientIdMapping = new IdMapping(openMrsEncounter.getPatient().getUuid(), HEALTH_ID, Constants.ID_MAPPING_PATIENT_TYPE, patientUrl);
        when(idMappingsRepository.findByInternalId(openMrsEncounter.getPatient().getUuid())).thenReturn(patientIdMapping);

        encounterPush.process(event);

        verify(encounterService).getEncounterByUuid(uuid);
        verify(shrClient).post("http://localhost:9997/" + patientUrl, bundle);
        ArgumentCaptor<IdMapping> idMappingArgumentCaptor = ArgumentCaptor.forClass(IdMapping.class);
        verify(idMappingsRepository).saveOrUpdateMapping(idMappingArgumentCaptor.capture());

        IdMapping idmapping = idMappingArgumentCaptor.getValue();
        assertEquals("shr-uuid", idmapping.getExternalId());
        assertEquals("shr-uuid", idmapping.getExternalId());
        assertEquals("encounter", idmapping.getType());
        assertEquals("http://localhost:9997/patients/" + HEALTH_ID + "/encounters/shr-uuid", idmapping.getUri());
    }

    @Test
    public void shouldProcessAddDrugOrdersToIdMapping() throws IOException {
        final String uuid = "123abc456";
        String facilityId = "10000069";

        final Event event = new Event("id100", "/openmrs/ws/rest/v1/encounter/" + uuid
                + "?v=custom:(uuid,encounterType,patient,visit,orders:(uuid,orderType,concept,voided))");
        org.openmrs.Encounter openMrsEncounter = getOpenMrsEncounter(uuid);
        DrugOrder drugOrder = new DrugOrder(10);
        OrderType drugOrderType = new OrderType();
        drugOrderType.setName(MRSProperties.MRS_DRUG_ORDER_TYPE);
        drugOrder.setOrderType(drugOrderType);
        openMrsEncounter.addOrder(drugOrder);
        final Bundle bundle = new Bundle();

        when(propertiesReader.getShrProperties()).thenReturn(getShrProperties(facilityId));
        when(encounterService.getEncounterByUuid(uuid)).thenReturn(openMrsEncounter);
        when(shrClient.post(anyString(), eq(bundle))).thenReturn("{\"encounterId\":\"shr-uuid\"}");
        when(compositionBundle.create(any(Encounter.class), eq(HEALTH_ID), any(SystemProperties.class))).thenReturn(bundle);
        when(idMappingsRepository.findByExternalId(facilityId)).thenReturn(null);
        IdMapping patientIdMapping = new IdMapping(openMrsEncounter.getPatient().getUuid(), HEALTH_ID, Constants.ID_MAPPING_PATIENT_TYPE, patientUrl);
        when(idMappingsRepository.findByInternalId(openMrsEncounter.getPatient().getUuid())).thenReturn(patientIdMapping);

        encounterPush.process(event);

        verify(encounterService).getEncounterByUuid(uuid);
        verify(shrClient).post("http://localhost:9997/" + patientUrl, bundle);
        ArgumentCaptor<IdMapping> idMappingArgumentCaptor = ArgumentCaptor.forClass(IdMapping.class);
        verify(idMappingsRepository, times(2)).saveOrUpdateMapping(idMappingArgumentCaptor.capture());

        List<IdMapping> idMappings = idMappingArgumentCaptor.getAllValues();
        String orderUrl = "http://localhost:9997/patients/" + HEALTH_ID + "/encounters/shr-uuid" + "#MedicationOrder/" + drugOrder.getUuid();
        assertTrue(containsIdMapping(idMappings, drugOrder.getUuid(), drugOrder.getUuid(), Constants.ID_MAPPING_ORDER_TYPE, orderUrl));
    }

    @Test
    public void shouldProcessEncounterUpdateEvent() throws Exception {
        final String uuid = "123abc456";
        String facilityId = "10000069";

        final Event event = new Event("id100", "/openmrs/ws/rest/v1/encounter/" + uuid
                + "?v=custom:(uuid,encounterType,patient,visit,orders:(uuid,orderType,concept,voided))");
        org.openmrs.Encounter openMrsEncounter = getOpenMrsEncounter(uuid);
        DateTime dateCreated = new DateTime(openMrsEncounter.getDateCreated());
        openMrsEncounter.setDateChanged(dateCreated.plusMinutes(10).toDate());
        final Bundle bundle = new Bundle();

        when(propertiesReader.getShrProperties()).thenReturn(getShrProperties(facilityId));
        when(encounterService.getEncounterByUuid(uuid)).thenReturn(openMrsEncounter);
        when(idMappingsRepository.findByInternalId(uuid)).thenReturn(new IdMapping(uuid, "shr-uuid", "encounter", null, openMrsEncounter.getDateCreated()));
        when(compositionBundle.create(any(Encounter.class), eq(HEALTH_ID), any(SystemProperties.class))).thenReturn(bundle);
        IdMapping patientIdMapping = new IdMapping(openMrsEncounter.getPatient().getUuid(), HEALTH_ID, Constants.ID_MAPPING_PATIENT_TYPE, patientUrl);
        when(idMappingsRepository.findByInternalId(openMrsEncounter.getPatient().getUuid())).thenReturn(patientIdMapping);

        encounterPush.process(event);

        verify(shrClient).put("http://localhost:9997/patients/" + HEALTH_ID + "/encounters/shr-uuid", bundle);
        verify(idMappingsRepository, times(1)).saveOrUpdateMapping(any(IdMapping.class));
    }

    @Test
    public void shouldNotProcessEncounterDownloadEvent() throws Exception {
        final String uuid = "123abc456";
        String facilityId = "10000069";

        final Event event = new Event("id100", "/openmrs/ws/rest/v1/encounter/" + uuid
                + "?v=custom:(uuid,encounterType,patient,visit,orders:(uuid,orderType,concept,voided))");
        org.openmrs.Encounter openMrsEncounter = getOpenMrsEncounter(uuid);
        final Bundle bundle = new Bundle();

        when(propertiesReader.getShrProperties()).thenReturn(getShrProperties(facilityId));
        when(encounterService.getEncounterByUuid(uuid)).thenReturn(openMrsEncounter);
        when(idMappingsRepository.findByInternalId(uuid)).thenReturn(new IdMapping(uuid, "shr-uuid", "encounter", null));
        when(compositionBundle.create(any(Encounter.class), eq(HEALTH_ID), any(SystemProperties.class))).thenReturn(bundle);
        when(systemUserService.isUpdatedByOpenMRSShrSystemUser(openMrsEncounter)).thenReturn(true);
        IdMapping patientIdMapping = new IdMapping(openMrsEncounter.getPatient().getUuid(), HEALTH_ID, Constants.ID_MAPPING_PATIENT_TYPE, patientUrl);
        when(idMappingsRepository.findByInternalId(openMrsEncounter.getPatient().getUuid())).thenReturn(patientIdMapping);

        encounterPush.process(event);

        verify(shrClient, never()).put("patients/" + HEALTH_ID + "/encounters/shr-uuid", bundle);
        verify(shrClient, never()).post("patients/" + HEALTH_ID + "/encounters/shr-uuid", bundle);
        verify(idMappingsRepository, never()).saveOrUpdateMapping(any(IdMapping.class));
    }

    @Test
    public void shouldGetEncounterUuidFromEventContent() throws Exception{
        final String uuid = "123abc456";
        final String content = "/openmrs/ws/rest/v1/encounter/" + uuid +
                "?v=custom:(uuid,encounterType,patient,visit,orders:(uuid,orderType,concept,voided))";
        assertEquals(uuid, encounterPush.getUuid(content));
    }

    @Test(expected = RuntimeException.class)
    public void shouldNotProcessEncounterIfPatientIsNotSynced() throws Exception{
        final String uuid = "123abc456";

        final Event event = new Event("id100", "/openmrs/ws/rest/v1/encounter/" + uuid
                + "?v=custom:(uuid,encounterType,patient,visit,orders:(uuid,orderType,concept,voided))");
        org.openmrs.Encounter openMrsEncounter = getOpenMrsEncounter(uuid);

        when(encounterService.getEncounterByUuid(uuid)).thenReturn(openMrsEncounter);
        when(idMappingsRepository.findByInternalId(openMrsEncounter.getPatient().getUuid())).thenReturn(null);

        encounterPush.process(event);
    }

    private boolean containsIdMapping(List<IdMapping> idMappings, String internalId, String externalId, String type, String url) {
        for (IdMapping idMapping : idMappings) {
            if(idMapping.getInternalId().equals(internalId)
                    && idMapping.getExternalId().equals(externalId)
                    && idMapping.getType().equals(type)
                    && idMapping.getUri().equals(url))
                return true;
        }
        return false;
    }

    private org.openmrs.Encounter getOpenMrsEncounter(String uuid) {
        org.openmrs.Encounter openMrsEncounter = new org.openmrs.Encounter();
        openMrsEncounter.setUuid(uuid);
        openMrsEncounter.setCreator(new User(1));
        openMrsEncounter.setDateCreated(new Date());
        final Patient patient = new Patient();
        openMrsEncounter.setPatient(patient);
        return openMrsEncounter;
    }

    private Properties getShrProperties(String facilityId) {
        Properties shrProperties = new Properties();
        shrProperties.setProperty(FACILITY_ID, facilityId);
        shrProperties.setProperty(SHR_REFERENCE_PATH, "http://localhost:9997");
        shrProperties.setProperty(SHR_PATIENT_ENC_PATH_PATTERN, "/patients/%s/encounters");
        return shrProperties;
    }
}
