package vn.haui.o2ofood.modules.fulfillment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PackageRepository extends JpaRepository<PackageEntity, Long> {
    List<PackageEntity> findByOrderIdOrderByPackageSeqAsc(Long orderId);
    List<PackageEntity> findByTripStopIdOrderByIdAsc(Long tripStopId);
    Optional<PackageEntity> findByPackageCode(String packageCode);
    Optional<PackageEntity> findByQrPayload(String qrPayload);
}
