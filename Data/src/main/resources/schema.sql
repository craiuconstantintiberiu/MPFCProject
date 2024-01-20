CREATE SCHEMA IF NOT EXISTS hospital_admin_db;
USE hospital_admin_db;

CREATE TABLE IF NOT EXISTS billing
(
    id         varchar(255)         not null
        primary key,
    patient_id varchar(255)         null,
    amount     decimal(10, 2)       null,
    paid       tinyint(1) default 0 null
);

CREATE TABLE IF NOT EXISTS doctors
(
    id                 varchar(255) not null
        primary key,
    name               varchar(255) null,
    specialization     varchar(255) null,
    number_of_patients int          null
);

CREATE TABLE IF NOT EXISTS patientanalytics
(
    patient_id   varchar(255) not null
        primary key,
    access_count varchar(255) null
);

CREATE SCHEMA IF NOT EXISTS hospital_db;
USE hospital_db;

CREATE TABLE IF NOT EXISTS appointments
(
    id               varchar(255) not null
        primary key,
    patient_id       varchar(255) null,
    doctor_id        varchar(255) null,
    appointment_date datetime     null,
    status           varchar(100) null
);

CREATE TABLE IF NOT EXISTS medicalrecords
(
    patient_id  varchar(255) null,
    notes       varchar(255) null,
    accessCount int          null
);

CREATE TABLE IF NOT EXISTS patient
(
    id              varchar(255)             not null
        primary key,
    name            varchar(255)             null,
    email           varchar(255)             null,
    hasBeenModified varchar(255) default '0' null,
    constraint name_email_unique
        unique (name, email)
);

