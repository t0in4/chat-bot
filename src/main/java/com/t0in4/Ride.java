package com.t0in4;

import jakarta.persistence.Entity;

@Entity
public class Ride {
    @jakarta.persistence.Id
    @jakarta.persistence.GeneratedValue
    private Long id;
    public String name;
    public double rating;

}
