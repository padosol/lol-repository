package com.mmrtr.lol.infra.persistence.version;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatchNoteJpaRepository extends JpaRepository<PatchNoteEntity, String> {

    List<PatchNoteEntity> findAllByOrderByCreatedAtDesc();
}
