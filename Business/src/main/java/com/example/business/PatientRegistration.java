package com.example.business;

import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.stream.IntStream;

@RestController
public class PatientRegistration {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String registerURL = "http://localhost:8080/register";
    private final String updateURL = "http://localhost:8080/update";
    private final String recordURL = "http://localhost:8080/record";

    private final String billingURL = "http://localhost:8080/bill";

    private final String stressURL = "http://localhost:8080/stress";

    private final String stress5DoctorsIncrementURL = "http://localhost:8080/stress5DoctorsIncrement";
    @PostMapping("/register")
    public String registerPatient(@RequestParam("name") String name,
                                  @RequestParam("email") String email) {
        // Forward the request to the Data Layer
        // Create a map to hold request parameters
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("name", name);
        params.add("email", email);
        if(!email.contains("@")) {
            return "Email is not valid!";
        }
        // Forward the request to the Data Layer
        ResponseEntity<String> response = restTemplate.postForEntity(registerURL, params, String.class);
        return response.getBody();
    }

    @PostMapping("/update")
    public String updatePatient(@RequestParam("name") String name,
                                @RequestParam("email") String email,
                                @RequestParam("notes") String notes) {
        // Forward the request to the Data Layer
        // Create a map to hold request parameters
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("name", name);
        params.add("email", email);
        params.add("notes", notes);

        // Forward the request to the Data Layer
        ResponseEntity<String> response = restTemplate.postForEntity(updateURL, params, String.class);
        return response.getBody();
    }

    @PostMapping("/record")
    public String updatePatient(@RequestParam("name") String name,
                                @RequestParam("email") String email) {
        // Forward the request to the Data Layer
        // Create a map to hold request parameters
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("name", name);
        params.add("email", email);

        // Forward the request to the Data Layer
        ResponseEntity<String> response = restTemplate.postForEntity(recordURL, params, String.class);
        return response.getBody();
    }

@PostMapping("/bill")
    public String getBill(@RequestParam("name") String name,
                                @RequestParam("email") String email) {
        // Forward the request to the Data Layer
        // Create a map to hold request parameters
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("name", name);
        params.add("email", email);

        // Forward the request to the Data Layer
        ResponseEntity<String> response = restTemplate.postForEntity(billingURL, params, String.class);
        return response.getBody();
    }

    @PostMapping("/stress")
    public String stressTest(@RequestParam("number") String number) {
        // Forward the request to the Data Layer
        // Create a map to hold request parameters
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();

        int num = Integer.parseInt(number);
        // Forward the request to the Data Layer
        IntStream.range(1,num).parallel().forEach(i -> {
            restTemplate.postForEntity(stressURL, params, String.class);
        });
        return "Stress testing!";
    }

    @PostMapping("/stress5DoctorsIncrement")
    public String stress5DoctorsIncrement(@RequestParam("number") String number) {
        int num = Integer.parseInt(number);
        // Forward the request to the Data Layer
        // Create a map to hold request parameters
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();

        // Forward the request to the Data Layer
        IntStream.range(1,num).parallel().forEach(i -> {
            restTemplate.postForEntity(stress5DoctorsIncrementURL, params, String.class);
        });
        return "Stress testing!";
    }

    @PostMapping("/decrementDates")
    public String decrementDates(@RequestParam("number") String number) {
        int num = Integer.parseInt(number);
        // Forward the request to the Data Layer
        // Create a map to hold request parameters
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();

        // Forward the request to the Data Layer
        IntStream.range(1,num).parallel().forEach(i -> {
            restTemplate.postForEntity("http://localhost:8080/decrementDates", params, String.class);
        });
        return "Stress testing!";
    }

    @PostMapping("stressAllTables")
    public String stressAllTables(@RequestParam("number") String number) {
        int num = Integer.parseInt(number);
        // Forward the request to the Data Layer
        // Create a map to hold request parameters
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();

        // Forward the request to the Data Layer
        IntStream.range(1,num).parallel().forEach(i -> {
            restTemplate.postForEntity("http://localhost:8080/stressAllTables", params, String.class);
        });
        return "Stress testing!";
    }

}