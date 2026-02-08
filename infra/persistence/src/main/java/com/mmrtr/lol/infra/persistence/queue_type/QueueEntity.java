package com.mmrtr.lol.infra.persistence.queue_type;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Getter
@Table(name = "queue_data")
public class QueueEntity {

    @Id
    @Column(name = "queue_id")
    private Long queueId;

    private String queueName;

    private boolean isTab = false;
}
