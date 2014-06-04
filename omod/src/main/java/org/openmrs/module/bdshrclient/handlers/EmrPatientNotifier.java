package org.openmrs.module.bdshrclient.handlers;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ict4h.atomfeed.client.service.FeedClient;
import org.openmrs.api.context.Context;
import org.openmrs.module.addresshierarchy.service.AddressHierarchyService;
import org.openmrs.module.bdshrclient.OpenMRSFeedClientFactory;

import java.net.URI;
import java.net.URISyntaxException;

public class EmrPatientNotifier {
    private static final Log log = LogFactory.getLog(EmrPatientNotifier.class);
    private static final String OPENMRS_PATIENT_FEED_URI = "openmrs://events/patient/recent";

    public void process() {
        OpenMRSFeedClientFactory factory = new OpenMRSFeedClientFactory();
        FeedClient feedClient = null;
        try {
            //TODO: Should only 1 instance of ShrPatientCreator be created ?
            feedClient = factory.getFeedClient(getEmrPatientUpdateUri(), new ShrPatientCreator(
                    Context.getService(AddressHierarchyService.class), Context.getPatientService()));
            feedClient.processEvents();
        } catch (URISyntaxException e) {
            log.error("Invalid URI. ", e);
        }
    }

    private URI getEmrPatientUpdateUri() throws URISyntaxException {
        return new URI(OPENMRS_PATIENT_FEED_URI);
    }
}
