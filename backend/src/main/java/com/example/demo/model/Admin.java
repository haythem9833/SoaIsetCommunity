package com.example.demo.model;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.*;

@Entity
@DiscriminatorValue("ADMIN")
@Getter
@Setter
public class Admin extends Prof {
}
