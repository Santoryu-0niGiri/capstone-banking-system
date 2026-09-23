package com.capstone.accounts.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "customer_master")
@Getter
@Setter
@NoArgsConstructor
public class Customer {

    @Id
    @Column(
        name = "customer_id",
        length = 36,
        nullable = false,
        updatable = false
    )
    private String customerId;
}