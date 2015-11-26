package org.openmrs.module.fhir.mapper.emr;

import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.model.dstu2.resource.Bundle;
import ca.uhn.fhir.model.dstu2.resource.DiagnosticOrder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.Encounter;
import org.openmrs.Order;
import org.openmrs.api.OrderService;
import org.openmrs.api.PatientService;
import org.openmrs.api.ProviderService;
import org.openmrs.module.fhir.MapperTestHelper;
import org.openmrs.module.fhir.mapper.model.ShrEncounterComposition;
import org.openmrs.module.fhir.utils.FHIRBundleHelper;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

import java.util.Date;
import java.util.Set;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.openmrs.module.fhir.MapperTestHelper.getSystemProperties;

@ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class FHIRDiagnosticOrderMapperIT extends BaseModuleWebContextSensitiveTest {
    @Autowired
    private ApplicationContext springContext;

    @Autowired
    private FHIRDiagnosticOrderMapper diagnosticOrderMapper;

    @Autowired
    private PatientService patientService;

    @Autowired
    private ProviderService providerService;

    @Autowired
    private OrderService orderService;

    public Bundle loadSampleFHIREncounter(String filePath) throws Exception {
        return (Bundle) new MapperTestHelper().loadSampleFHIREncounter(filePath, springContext);
    }

    @Before
    public void setUp() throws Exception {
        executeDataSet("testDataSets/labOrderDS.xml");
    }

    @After
    public void tearDown() throws Exception {
        deleteAllData();
    }

    @Test
    public void shouldMapDiagnosticOrder() throws Exception {
        Encounter encounter = mapOrder("encounterBundles/dstu2/encounterWithDiagnosticOrder.xml");
        Set<Order> orders = encounter.getOrders();
        assertFalse(orders.isEmpty());
        assertEquals(1, orders.size());
        Order order = orders.iterator().next();
        assertEquals("7f7379ba-3ca8-11e3-bf2b-0800271c1b75", order.getConcept().getUuid());
        assertEquals(providerService.getProvider(23), order.getOrderer());
        assertEquals(orderService.getOrderType(16), order.getOrderType());
        assertEquals(orderService.getCareSetting(1), order.getCareSetting());
        assertNotNull(order.getDateActivated());
    }

    @Test
    public void shouldMapDiagnosticOrderWithoutOrderer() throws Exception {
        int shrClientSystemProviderId = 22;
        Encounter encounter = mapOrder("encounterBundles/dstu2/encounterWithDiagnosticOrderWithoutOrderer.xml");
        Set<Order> orders = encounter.getOrders();
        assertEquals(1, orders.size());
        Order order = orders.iterator().next();
        assertThat(order.getOrderer().getProviderId(), is(shrClientSystemProviderId));
    }

    private Encounter mapOrder(String filePath) throws Exception {
        Bundle bundle = loadSampleFHIREncounter(filePath);
        IResource resource = FHIRBundleHelper.identifyResource(bundle.getEntry(), new DiagnosticOrder().getResourceName());
        Encounter encounter = new Encounter();
        encounter.setEncounterDatetime(new Date());
        ShrEncounterComposition encounterComposition = new ShrEncounterComposition(bundle, "HIDA764177", "shr-enc-id-1");
        diagnosticOrderMapper.map(resource, encounter, encounterComposition, getSystemProperties("1"));
        return encounter;
    }
}