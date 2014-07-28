package org.openmrs.module.shrclient.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PatientJsonTest {

    private Patient patient;

    @Before
    public void setup() {
        patient = new Patient();
        patient.setFirstName("Scott");
        patient.setLastName("Tiger");
        patient.setGender("1");
        Address address = new Address();
        address.setDivisionId("10");
        address.setDistrictId("1020");
        address.setUpazillaId("102030");
        address.setUnionId("10203040");
        patient.setAddress(address);
    }

    @Test
    public void shouldExcludeEmptyNonMandatoryFields() throws Exception {
        patient.setMiddleName("");
        String expected = "{\"first_name\":\"Scott\",\"last_name\":\"Tiger\",\"date_of_birth\":null,\"gender\":\"1\"" +
                ",\"present_address\":{\"address_line\":null,\"division_id\":\"10\",\"district_id\":\"1020\"" +
                ",\"upazilla_id\":\"102030\",\"union_id\":\"10203040\"}}";
        String actual = new ObjectMapper().writeValueAsString(patient);
        assertEquals(expected, actual);
    }

    @Test
    public void shouldIncludeNonEmptyNonMandatoryFields() throws Exception {
        patient.setNationalId("nid-100");
        patient.setMiddleName(null);
        String expected = "{\"nid\":\"nid-100\",\"first_name\":\"Scott\",\"last_name\":\"Tiger\",\"date_of_birth\":null," +
                "\"gender\":\"1\",\"present_address\":{\"address_line\":null,\"division_id\":\"10\",\"district_id\":\"1020\"," +
                "\"upazilla_id\":\"102030\",\"union_id\":\"10203040\"}}";
        String actual = new ObjectMapper().writeValueAsString(patient);
        assertEquals(expected, actual);
    }

}