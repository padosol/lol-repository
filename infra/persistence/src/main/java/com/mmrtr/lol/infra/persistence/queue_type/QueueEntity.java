package com.mmrtr.lol.infra.persistence.queue_type;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Comment;

@Entity
@Getter
@Table(name = "queue_data")
public class QueueEntity {

    @Id
    @Comment("큐 ID")
    private Long queueId;

    @Comment("큐 이름")
    private String queueName;

    @Comment("탭 표시 여부")
    private boolean isTab = false;
}
