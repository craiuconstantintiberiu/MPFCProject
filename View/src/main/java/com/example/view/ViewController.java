package com.example.view;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ViewController {

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/register")
    public String patientRegistrationForm() {
        return "patientRegistration";
    }

    @GetMapping("/update")
    public String patientUpdateForm() {
        return "patientUpdate";
    }

    @GetMapping("/record")
    public String patientRecord() {
        return "patientRecord";
    }

    @GetMapping("/bill")
    public String patientBilling() {
        return "patientBilling";
    }

    @GetMapping("/stress")
    public String stressTest() {
        return "stressTest";
    }

    @GetMapping("/stress5DoctorsIncrement")
    public String stress5DoctorsIncrement(){
        return "stress5DoctorsIncrement";
    }

    @GetMapping("/decrementDates")
    public String decrementDates(){
        return "decrementDates";
    }

    @GetMapping("/stressAllTables")
    public String stressAllTables(){
        return "stressAllTables";
    }
}
