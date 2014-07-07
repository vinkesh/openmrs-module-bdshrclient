package org.bahmni.module.shrclient.handlers;


import org.apache.log4j.Logger;
import org.bahmni.module.shrclient.OpenMRSFeedClientFactory;
import org.bahmni.module.shrclient.mapper.EncounterMapper;
import org.bahmni.module.shrclient.mapper.PatientMapper;
import org.bahmni.module.shrclient.service.impl.BbsCodeServiceImpl;
import org.bahmni.module.shrclient.util.FhirRestClient;
import org.bahmni.module.shrclient.util.RestClient;
import org.ict4h.atomfeed.client.service.EventWorker;
import org.ict4h.atomfeed.client.service.FeedClient;
import org.openmrs.api.context.Context;
import org.openmrs.module.addresshierarchy.service.AddressHierarchyService;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

public class ShrNotifier {

    private static final Logger log = Logger.getLogger(ShrNotifier.class);

    private static final String OPENMRS_PATIENT_FEED_URI = "openmrs://events/patient/recent";
    private static final String OPENMRS_ENCOUNTER_FEED_URI = "openmrs://events/encounter/recent";

    public void processPatient() {
        process(OPENMRS_PATIENT_FEED_URI, new ShrPatientCreator(
                Context.getPatientService(),
                Context.getUserService(),
                Context.getPersonService(),
                new PatientMapper(Context.getService(AddressHierarchyService.class), new BbsCodeServiceImpl()),
                getMciWebClient()));
    }

    public void processEncounter() {
        process(OPENMRS_ENCOUNTER_FEED_URI, new ShrEncounterCreator(
                Context.getEncounterService(),
                new EncounterMapper(),
                getShrWebClient()));
    }

    private void process(String feedURI, EventWorker eventWorker) {
        OpenMRSFeedClientFactory factory = new OpenMRSFeedClientFactory();
        try {
            FeedClient feedClient = factory.getFeedClient(new URI(feedURI), eventWorker);
            feedClient.processEvents();

        } catch (URISyntaxException e) {
            log.error("Invalid URI. ", e);
            throw new RuntimeException(e);
        }
    }

    private RestClient getMciWebClient() {
        Properties properties = getProperties("mci.properties");
        return new RestClient(properties.getProperty("mci.user"),
                properties.getProperty("mci.password"),
                properties.getProperty("mci.host"),
                properties.getProperty("mci.port"));
    }

    private FhirRestClient getShrWebClient() {
        Properties properties = getProperties("shr.properties");
        return new FhirRestClient(properties.getProperty("shr.user"),
                properties.getProperty("shr.password"),
                properties.getProperty("shr.host"),
                properties.getProperty("shr.port"));
    }

    Properties getProperties(String resource) {
        try {
            Properties properties = new Properties();
            final File file = new File(System.getProperty("user.home") + File.separator + "shr" + File.separator
                    + "config" + File.separator + resource);
            final InputStream inputStream;
            if (file.exists()) {
                inputStream = new FileInputStream(file);
            } else {
                inputStream = getClass().getClassLoader().getResourceAsStream(resource);
            }
            properties.load(inputStream);
            return properties;

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}