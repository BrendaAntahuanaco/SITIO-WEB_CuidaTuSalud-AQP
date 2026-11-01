
package com.campanas.salud.repository;

import com.campanas.salud.model.Campana;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CampanaRepository extends JpaRepository<Campana, Long> {
    /** Filtra por vacuna y distrito sin distinguir mayúsculas (RF-10). */
    List<Campana> findByVacunaContainingIgnoreCaseAndEstablecimientoDistritoContainingIgnoreCase(
            String vacuna, String distrito);
}
