package com.example.data;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping
public class PatientController {

    @Autowired
    private JdbcTemplate jdbcTemplateHospitalDB;

    @Autowired
    private JdbcTemplate jdbcTemplateHospitalAdminDB;

    static enum Table {
        PATIENT,
        APPPOINTMENT,
        MEDICAL_RECORD,
        BILLING,
        DOCTOR,
        PATIENT_ANALYTICS,
    }

    Map<Table, String> tableToDB = Map.of(
            Table.PATIENT, "patient",
            Table.APPPOINTMENT, "appointments",
            Table.MEDICAL_RECORD, "medicalrecords",
            Table.BILLING, "billing",
            Table.DOCTOR, "doctors",
            Table.PATIENT_ANALYTICS, "patientanalytics"
    );

    @ExceptionHandler(Exception.class)
    public String handleException(Exception e) {
        return e.getMessage();
    }

    @PostMapping("/register")
    public String addPatient(@RequestParam String name, @RequestParam String email) {
        String patientUUID = java.util.UUID.randomUUID().toString();

        Operation patientOperation = new Operation.OperationBuilder()
                .operationType(OperationType.INSERT)
                .sql(String.format("INSERT INTO patient (id, name, email) VALUES ('%s', '%s', '%s')", patientUUID, name, email))
                .rollbackSql("DELETE FROM patient WHERE id = " + patientUUID)
        .dbType(dbType.HOSPITAL_DB)
                .lockType(LockType.WRITE)
        .rowIdentifier(new RowIdentifier.RowIdentifierBuilder().rowId(patientUUID).tableName("patient").build())
                .build();


        String billingUUID = java.util.UUID.randomUUID().toString();

        Operation billingOperation = new Operation.OperationBuilder()
                .operationType(OperationType.INSERT)
                .sql(String.format("INSERT INTO billing (id, patient_id, amount) VALUES ('%s', '%s', '%s')", billingUUID, patientUUID, 100))
                .rollbackSql("DELETE FROM billing WHERE id = " + billingUUID)
                .lockType(LockType.WRITE)
                .dbType(dbType.HOSPITAL_ADMIN_DB)
                .rowIdentifier(new RowIdentifier.RowIdentifierBuilder().rowId(billingUUID).tableName("billing").build())
                .build();
        // create a random number from 1 to 5, then use that to select a doctor
        int doctorNumber = (int) (Math.random() * 5 + 1);
        Operation doctorOperation = new Operation.OperationBuilder()
                .operationType(OperationType.UPDATE)
                .sql(String.format("UPDATE doctors SET number_of_patients = number_of_patients + 1 WHERE id = '%s'", doctorNumber))
                .rollbackSql("UPDATE doctors SET number_of_patients = number_of_patients - 1 WHERE id = " + doctorNumber)
                .dbType(dbType.HOSPITAL_ADMIN_DB)
                .lockType(LockType.WRITE)
                .rowIdentifier(new RowIdentifier.RowIdentifierBuilder().rowId(String.valueOf(doctorNumber)).tableName("doctor").build())
                .build();

        Operation medicalRecordOperation = new Operation.OperationBuilder()
                .operationType(OperationType.INSERT)
                .sql(String.format("INSERT INTO medicalrecords (patient_id, notes) VALUES ('%s', '%s')", patientUUID, ""))
                .rollbackSql("DELETE FROM medicalrecords WHERE patient_id = " + patientUUID)
                .rowIdentifier(new RowIdentifier.RowIdentifierBuilder().rowId(patientUUID).tableName("medicalrecords").build())
                .dbType(dbType.HOSPITAL_DB)
                .lockType(LockType.WRITE)
                .build();

        Operation patientAnalyticsOperation = new Operation.OperationBuilder()
                .operationType(OperationType.INSERT)
                .sql(String.format("INSERT INTO patientanalytics (patient_id, access_count) VALUES ('%s', '%s')", patientUUID, 0))
                .rollbackSql("DELETE FROM patientanalytics WHERE patient_id = " + patientUUID)
                .rowIdentifier(new RowIdentifier.RowIdentifierBuilder().rowId(patientUUID).tableName("patientanalytics").build())
                .dbType(dbType.HOSPITAL_ADMIN_DB)
                .lockType(LockType.WRITE)
                .build();

        Transaction transaction = new Transaction();
        transaction.addOperation(patientOperation);
        transaction.addOperation(billingOperation);
        transaction.addOperation(doctorOperation);
        transaction.addOperation(medicalRecordOperation);
        transaction.addOperation(patientAnalyticsOperation);
        transaction.jdbcTemplateHospitalDB = jdbcTemplateHospitalDB;
        transaction.jdbcTemplateHospitalAdminDB = jdbcTemplateHospitalAdminDB;

        transaction.transactionExecutor.run();

        return "Registration succesful!";
    }

    @PostMapping("/update")
    public String updatePatient(@RequestParam String name, @RequestParam String email, @RequestParam String notes) {
        //update patient in database here
        Operation getPatientID = new Operation.OperationBuilder()
                .operationType(OperationType.SELECT)
                .sql(String.format("SELECT id FROM patient WHERE name = '%s' AND email = '%s'", name, email))
                .rollbackSql("")
                .dbType(dbType.HOSPITAL_DB)
                .lockType(LockType.WRITE)
                .build();

        Operation getInitialMedicalRecord = new Operation.OperationBuilder()
                .operationType(OperationType.UPDATE)
                .parentOperation(getPatientID)
                .sql(String.format("SELECT notes from medicalrecords WHERE patient_id = 'NEEDED_VALUE'"))
                .rowIdentifier(new RowIdentifier.RowIdentifierBuilder().rowId("NEEDED_VALUE").tableName("medicalrecords").build())
                .dbType(dbType.HOSPITAL_DB)
                .lockType(LockType.WRITE)
                .build();

        Operation updateMedicalRecord = new Operation.OperationBuilder()
                .operationType(OperationType.UPDATE)
                .parentOperation(getPatientID)
                .parentOperation2(getInitialMedicalRecord)
                .sql(String.format("UPDATE medicalrecords SET notes = '%s' WHERE patient_id = 'NEEDED_VALUE'", notes))
                .rollbackSql(String.format("UPDATE medicalrecords SET notes = 'NEEDED_2' WHERE patient_id = 'NEEDED_VALUE'"))
                .rowIdentifier(new RowIdentifier.RowIdentifierBuilder().rowId("NEEDED_VALUE").tableName("medicalrecords").build())
                .dbType(dbType.HOSPITAL_DB)
                .lockType(LockType.WRITE)
                .build();

        Operation patientAnalyticsIncrementAccessCount = new Operation.OperationBuilder()
                .operationType(OperationType.UPDATE)
                .parentOperation(getPatientID)
                .sql(String.format("UPDATE patientanalytics SET access_count = access_count + 1 WHERE patient_id = 'NEEDED_VALUE'"))
                .rollbackSql(String.format("UPDATE patientanalytics SET access_count = access_count - 1 WHERE patient_id = 'NEEDED_VALUE'"))
                .rowIdentifier(new RowIdentifier.RowIdentifierBuilder().rowId("NEEDED_VALUE").tableName("patientanalytics").build())
                .dbType(dbType.HOSPITAL_ADMIN_DB)
                .lockType(LockType.WRITE)
                .build();

        Transaction transaction = new Transaction();
        transaction.addOperation(getPatientID);
        transaction.addOperation(updateMedicalRecord);
        transaction.addOperation(patientAnalyticsIncrementAccessCount);
        transaction.jdbcTemplateHospitalDB = jdbcTemplateHospitalDB;
        transaction.jdbcTemplateHospitalAdminDB = jdbcTemplateHospitalAdminDB;

        transaction.transactionExecutor.run();

        return "Patient notes updated";
    }

    @PostMapping("/record")
    public String getPatientRecord(@RequestParam String name, @RequestParam String email) {
        //get patient record from database here
        Operation getPatientID = new Operation.OperationBuilder()
                .operationType(OperationType.SELECT)
                .sql(String.format("SELECT id FROM patient WHERE name = '%s' AND email = '%s'", name, email))
                .rollbackSql("")
                .dbType(dbType.HOSPITAL_DB)
                .lockType(LockType.READ)
                .build();

        Operation getPatientRecord = new Operation.OperationBuilder()
                .operationType(OperationType.SELECT)
                .parentOperation(getPatientID)
                .sql(String.format("SELECT notes FROM medicalrecords WHERE patient_id = 'NEEDED_VALUE'"))
                .rowIdentifier(new RowIdentifier.RowIdentifierBuilder().rowId("NEEDED_VALUE").tableName("medicalrecords").build())
                .rollbackSql("")
                .dbType(dbType.HOSPITAL_DB)
                .lockType(LockType.READ)
                .build();

        Operation patientAnalyticsIncrementAccessCount = new Operation.OperationBuilder()
                .operationType(OperationType.UPDATE)
                .parentOperation(getPatientID)
                .sql(String.format("UPDATE patientanalytics SET access_count = access_count + 1 WHERE patient_id = 'NEEDED_VALUE'"))
                .rollbackSql(String.format("UPDATE patientanalytics SET access_count = access_count - 1 WHERE patient_id = 'NEEDED_VALUE'"))
                .rowIdentifier(new RowIdentifier.RowIdentifierBuilder().rowId("NEEDED_VALUE").tableName("patientanalytics").build())
                .dbType(dbType.HOSPITAL_ADMIN_DB)
                .lockType(LockType.WRITE)
                .build();

        Transaction transaction = new Transaction();
        transaction.addOperation(getPatientID);
        transaction.addOperation(getPatientRecord);
        transaction.addOperation(patientAnalyticsIncrementAccessCount);
        transaction.jdbcTemplateHospitalDB = jdbcTemplateHospitalDB;
        transaction.jdbcTemplateHospitalAdminDB = jdbcTemplateHospitalAdminDB;

        transaction.transactionExecutor.run();
        while(!transaction.isCommitted) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        return getPatientRecord.getResult();

    }

    @PostMapping("/bill")
    public String getPatientBill(@RequestParam String name, @RequestParam String email) {
        //get patient bill from database here
        Operation getPatientID = new Operation.OperationBuilder()
                .operationType(OperationType.SELECT)
                .sql(String.format("SELECT id FROM patient WHERE name = '%s' AND email = '%s'", name, email))
                .rollbackSql("")
                .dbType(dbType.HOSPITAL_DB)
                .lockType(LockType.READ)
                .build();

        //get bill aggregate sum
        Operation getPatientBill = new Operation.OperationBuilder()
                .operationType(OperationType.SELECT)
                .parentOperation(getPatientID)
                .sql(String.format("SELECT SUM(amount) FROM billing WHERE patient_id = 'NEEDED_VALUE'"))
                .rollbackSql("")
                .rowIdentifier(new RowIdentifier.RowIdentifierBuilder().rowId("NEEDED_VALUE").tableName("billing").build())
                .dbType(dbType.HOSPITAL_ADMIN_DB)
                .lockType(LockType.READ)
                .build();

        Operation patientAnalyticsIncrementAccessCount = new Operation.OperationBuilder()
                .operationType(OperationType.UPDATE)
                .parentOperation(getPatientID)
                .sql(String.format("UPDATE patientanalytics SET access_count = access_count + 1 WHERE patient_id = 'NEEDED_VALUE'"))
                .rollbackSql(String.format("UPDATE patientanalytics SET access_count = access_count - 1 WHERE patient_id = 'NEEDED_VALUE'"))
                .rowIdentifier(new RowIdentifier.RowIdentifierBuilder().rowId("NEEDED_VALUE").tableName("patientanalytics").build())
                .dbType(dbType.HOSPITAL_ADMIN_DB)
                .lockType(LockType.WRITE)
                .build();

        Transaction transaction = new Transaction();
        transaction.addOperation(getPatientID);
        transaction.addOperation(getPatientBill);
        transaction.addOperation(patientAnalyticsIncrementAccessCount);
        transaction.jdbcTemplateHospitalDB = jdbcTemplateHospitalDB;
        transaction.jdbcTemplateHospitalAdminDB = jdbcTemplateHospitalAdminDB;

        transaction.transactionExecutor.run();
        while(!transaction.isCommitted) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.out.println(getPatientBill.getResult()!=null?getPatientBill.getResult():"No bill found");
        return getPatientBill.getResult();

    }

    @PostMapping("/stress")
    public String stressTest() {
        //get patient bill from database here

        String patientUUID = java.util.UUID.randomUUID().toString();

        Operation patientOperation = new Operation.OperationBuilder()
                .operationType(OperationType.INSERT)
                .sql(String.format("INSERT INTO patient (id, name, email) VALUES ('%s', '%s', '%s')", patientUUID, patientUUID, patientUUID))
                .rollbackSql(String.format("DELETE FROM patient WHERE id = '%s'", patientUUID))
                .dbType(dbType.HOSPITAL_DB)
                .lockType(LockType.WRITE)
                .rowIdentifier(new RowIdentifier.RowIdentifierBuilder().rowId(patientUUID).tableName("patient").build())
                .build();


        String billingUUID = java.util.UUID.randomUUID().toString();

        Operation billingOperation = new Operation.OperationBuilder()
                .operationType(OperationType.INSERT)
                .sql(String.format("INSERT INTO billing (id, patient_id, amount) VALUES ('%s', '%s', '%s')", billingUUID, patientUUID, 100))
                .rollbackSql(String.format("DELETE FROM billing WHERE id = '%s'", billingUUID))
                .dbType(dbType.HOSPITAL_ADMIN_DB)
                .lockType(LockType.WRITE)
                .rowIdentifier(new RowIdentifier.RowIdentifierBuilder().rowId(billingUUID).tableName("billing").build())
                .build();
        // create a random number from 1 to 5, then use that to select a doctor
        int doctorNumber = (int) (Math.random() * 5 + 1);
        Operation doctorOperation = new Operation.OperationBuilder()
                .operationType(OperationType.UPDATE)
                .sql(String.format("UPDATE doctors SET number_of_patients = number_of_patients + 1 WHERE id = '%s'", doctorNumber))
                .rollbackSql(String.format("UPDATE doctors SET number_of_patients = number_of_patients - 1 WHERE id = '%s'", doctorNumber))
                .dbType(dbType.HOSPITAL_ADMIN_DB)
                .lockType(LockType.WRITE)
                .rowIdentifier(new RowIdentifier.RowIdentifierBuilder().rowId(String.valueOf(doctorNumber)).tableName("doctor").build())
                .build();

        Operation medicalRecordOperation = new Operation.OperationBuilder()
                .operationType(OperationType.INSERT)
                .sql(String.format("INSERT INTO medicalrecords (patient_id, notes) VALUES ('%s', '%s')", patientUUID, ""))
                .rollbackSql(String.format("DELETE FROM medicalrecords WHERE patient_id = '%s'", patientUUID))
                .rowIdentifier(new RowIdentifier.RowIdentifierBuilder().rowId(patientUUID).tableName("medicalrecords").build())
                .dbType(dbType.HOSPITAL_DB)
                .lockType(LockType.WRITE)
                .build();

        Operation patientAnalyticsOperation = new Operation.OperationBuilder()
                .operationType(OperationType.INSERT)
                .sql(String.format("INSERT INTO patientanalytics (patient_id, access_count) VALUES ('%s', '%s')", patientUUID, 0))
                .rollbackSql(String.format("DELETE FROM patientanalytics WHERE patient_id = '%s'", patientUUID) )
                .rowIdentifier(new RowIdentifier.RowIdentifierBuilder().rowId(patientUUID).tableName("patientanalytics").build())
                .dbType(dbType.HOSPITAL_ADMIN_DB)
                .lockType(LockType.WRITE)
                .build();

        String appointmentUUID = java.util.UUID.randomUUID().toString();

        Operation appointmentOperation = new Operation.OperationBuilder()
                .operationType(OperationType.INSERT)
                .sql(String.format("INSERT INTO appointments (id, patient_id, doctor_id, appointment_date) VALUES ('%s', '%s', '%s', '%s')", appointmentUUID, patientUUID, doctorNumber, "2021-04-01"))
                .rollbackSql(String.format("DELETE FROM appointments WHERE id = '%s'", appointmentUUID))
                .dbType(dbType.HOSPITAL_DB)
                .lockType(LockType.WRITE)
                .rowIdentifier(new RowIdentifier.RowIdentifierBuilder().rowId(appointmentUUID).tableName("appointments").build())
                .build();



        Transaction transaction = new Transaction();
        transaction.addOperation(patientOperation);
        transaction.addOperation(billingOperation);
        transaction.addOperation(doctorOperation);
        transaction.addOperation(medicalRecordOperation);
        transaction.addOperation(patientAnalyticsOperation);
        transaction.addOperation(appointmentOperation);
        transaction.jdbcTemplateHospitalDB = jdbcTemplateHospitalDB;
        transaction.jdbcTemplateHospitalAdminDB = jdbcTemplateHospitalAdminDB;

        transaction.transactionExecutor.run();
        return "Stress test successful";
    }

    @PostMapping("/stress5DoctorsIncrement")
    public void stress5DoctorsIncrement() {


        int numberDoctors = 5;
        Transaction transaction = new Transaction();

        for(int i = 0; i < numberDoctors; i++) {
            int doctorNumber = (int) (Math.random() * 5 + 1);
            transaction.addOperation(new Operation.OperationBuilder()
                    .operationType(OperationType.UPDATE)
                    .sql(String.format("UPDATE doctors SET number_of_patients = number_of_patients + 1 WHERE id = '%s'", doctorNumber))
                    .rollbackSql(String.format("UPDATE doctors SET number_of_patients = number_of_patients - 1 WHERE id = '%s'", doctorNumber))
                    .dbType(dbType.HOSPITAL_ADMIN_DB)
                    .lockType(LockType.WRITE)
                    .rowIdentifier(new RowIdentifier.RowIdentifierBuilder().rowId(String.valueOf((int) (Math.random() * 5 + 1))).tableName("doctor").build())
                    .build());
        }



        transaction.jdbcTemplateHospitalDB = jdbcTemplateHospitalDB;
        transaction.jdbcTemplateHospitalAdminDB = jdbcTemplateHospitalAdminDB;

        transaction.transactionExecutor.run();
    }

    @PostMapping("/decrementDates")
    public void modifyDates() {
        //get patient bill from database here

        Operation getRandomPatient = new Operation.OperationBuilder()
                .operationType(OperationType.SELECT)
                .sql(String.format("SELECT id FROM patient ORDER BY RAND() LIMIT 1"))
                .rollbackSql("")
                .dbType(dbType.HOSPITAL_DB)
                .lockType(LockType.READ)
                .build();

        Operation decrementDate = new Operation.OperationBuilder()
                .operationType(OperationType.UPDATE)
                .parentOperation(getRandomPatient)
                .sql(String.format("UPDATE appointments SET appointment_date = DATE_SUB(appointment_date, INTERVAL 1 DAY) WHERE patient_id = 'NEEDED_VALUE'"))
                .rollbackSql(String.format("UPDATE appointments SET appointment_date = DATE_ADD(appointment_date, INTERVAL 1 DAY) WHERE patient_id = 'NEEDED_VALUE'"))
                .rowIdentifier(new RowIdentifier.RowIdentifierBuilder().rowId("NEEDED_VALUE").tableName("appointments").build())
                .dbType(dbType.HOSPITAL_DB)
                .lockType(LockType.WRITE)
                .build();

        Operation incrementPatientAccessCount = new Operation.OperationBuilder()
                .operationType(OperationType.UPDATE)
                .parentOperation(getRandomPatient)
                .sql(String.format("UPDATE patientanalytics SET access_count = access_count + 1 WHERE patient_id = 'NEEDED_VALUE'"))
                .rollbackSql(String.format("UPDATE patientanalytics SET access_count = access_count - 1 WHERE patient_id = 'NEEDED_VALUE'"))
                .dbType(dbType.HOSPITAL_ADMIN_DB)
                .lockType(LockType.WRITE)
                .build();

        Transaction transaction = new Transaction();
        transaction.addOperation(getRandomPatient);
        transaction.addOperation(decrementDate);
        transaction.addOperation(incrementPatientAccessCount);
        transaction.jdbcTemplateHospitalDB = jdbcTemplateHospitalDB;
        transaction.jdbcTemplateHospitalAdminDB = jdbcTemplateHospitalAdminDB;

        transaction.transactionExecutor.run();
    }

    @PostMapping("/stressAllTables")
    public void megaTransaction () {
        Operation getRandomPatient = new Operation.OperationBuilder()
                .operationType(OperationType.SELECT)
                .sql(String.format("SELECT id FROM patient ORDER BY RAND() LIMIT 1"))
                .rollbackSql("")
                .dbType(dbType.HOSPITAL_DB)
                .lockType(LockType.READ)
                .build();

        Operation decrementDate = new Operation.OperationBuilder()
                .operationType(OperationType.UPDATE)
                .parentOperation(getRandomPatient)
                .sql(String.format("UPDATE appointments SET appointment_date = DATE_SUB(appointment_date, INTERVAL 1 DAY) WHERE patient_id = 'NEEDED_VALUE'"))
                .rollbackSql(String.format("UPDATE appointments SET appointment_date = DATE_ADD(appointment_date, INTERVAL 1 DAY) WHERE patient_id = 'NEEDED_VALUE'"))
                .rowIdentifier(new RowIdentifier.RowIdentifierBuilder().rowId("NEEDED_VALUE").tableName("appointments").build())
                .dbType(dbType.HOSPITAL_DB)
                .lockType(LockType.WRITE)
                .build();

        Operation incrementPatientAccessCount = new Operation.OperationBuilder()
                .operationType(OperationType.UPDATE)
                .parentOperation(getRandomPatient)
                .sql(String.format("UPDATE patientanalytics SET access_count = access_count + 1 WHERE patient_id = 'NEEDED_VALUE'"))
                .rollbackSql(String.format("UPDATE patientanalytics SET access_count = access_count - 1 WHERE patient_id = 'NEEDED_VALUE'"))
                .rowIdentifier(new RowIdentifier.RowIdentifierBuilder().rowId("NEEDED_VALUE").tableName("patientanalytics").build())
                .dbType(dbType.HOSPITAL_ADMIN_DB)
                .lockType(LockType.WRITE)
                .build();

        Operation increaseBill = new Operation.OperationBuilder()
                .operationType(OperationType.UPDATE)
                .parentOperation(getRandomPatient)
                .sql(String.format("UPDATE billing SET amount = amount + 1 WHERE patient_id = 'NEEDED_VALUE'"))
                .rollbackSql(String.format("UPDATE billing SET amount = amount - 1 WHERE patient_id = 'NEEDED_VALUE'"))
                .rowIdentifier(new RowIdentifier.RowIdentifierBuilder().rowId("NEEDED_VALUE").tableName("billing").build())
                .dbType(dbType.HOSPITAL_ADMIN_DB)
                .lockType(LockType.WRITE)
                .build();

        Operation modifyNotes = new Operation.OperationBuilder()
                .operationType(OperationType.UPDATE)
                .parentOperation(getRandomPatient)
                .sql(String.format("UPDATE medicalrecords SET notes = 'modified' WHERE patient_id = 'NEEDED_VALUE'"))
                .rollbackSql(String.format("UPDATE medicalrecords SET notes = '' WHERE patient_id = 'NEEDED_VALUE'"))
                .dbType(dbType.HOSPITAL_DB)
                .lockType(LockType.WRITE)
                .rowIdentifier(new RowIdentifier.RowIdentifierBuilder().rowId("NEEDED_VALUE").tableName("medicalrecords").build())
                .build();

        Operation modifyName = new Operation.OperationBuilder()
                .operationType(OperationType.UPDATE)
                .parentOperation(getRandomPatient)
                .sql(String.format("UPDATE patient SET name = 'modified' WHERE id = 'NEEDED_VALUE'"))
                .rollbackSql(String.format("UPDATE patient SET name = '' WHERE id = 'NEEDED_VALUE'"))
                .dbType(dbType.HOSPITAL_DB)
                .rowIdentifier(new RowIdentifier.RowIdentifierBuilder().rowId("NEEDED_VALUE").tableName("patient").build())
                .lockType(LockType.WRITE)
                .build();

        Operation modifyEmail = new Operation.OperationBuilder()
                .operationType(OperationType.UPDATE)
                .parentOperation(getRandomPatient)
                .sql(String.format("UPDATE patient SET email = 'modified' WHERE id = 'NEEDED_VALUE'"))
                .rollbackSql(String.format("UPDATE patient SET email = '' WHERE id = 'NEEDED_VALUE'"))
                .rowIdentifier(new RowIdentifier.RowIdentifierBuilder().rowId("NEEDED_VALUE").tableName("patient").build())
                .dbType(dbType.HOSPITAL_DB)
                .lockType(LockType.WRITE)
                .build();

        Transaction transaction = new Transaction();
        transaction.addOperation(getRandomPatient);
        transaction.addOperation(decrementDate);
        transaction.addOperation(incrementPatientAccessCount);
        transaction.addOperation(increaseBill);
        transaction.addOperation(modifyNotes);
        transaction.jdbcTemplateHospitalDB = jdbcTemplateHospitalDB;
        transaction.jdbcTemplateHospitalAdminDB = jdbcTemplateHospitalAdminDB;

        transaction.transactionExecutor.run();
    }
}