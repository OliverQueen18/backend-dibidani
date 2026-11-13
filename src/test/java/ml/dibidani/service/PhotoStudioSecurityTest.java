package ml.dibidani.service;

import ml.dibidani.domain.*;
import ml.dibidani.repository.*;
import ml.dibidani.api.dto.PhotoStudioDtos.ConfirmRequest;
import ml.dibidani.config.PhotoStudioProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import tools.jackson.databind.ObjectMapper;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PhotoStudioSecurityTest {
  @Test void sellerCannotReadOrAttachAnotherSellersOriginal() {
    var assets=mock(ProductImageAssetRepository.class);
    var users=mock(AppUserRepository.class);
    var vendors=mock(VendorRepository.class);
    var files=mock(ProductImageStorageService.class);
    var service=new ProductImageService(assets,mock(ProductImageJobRepository.class),files,
        mock(ProductImageWatermarkService.class),mock(ProductImageJobService.class),mock(ProductImageProcessingService.class),
        mock(ProductRepository.class),users,vendors,new PhotoStudioProperties(),new ObjectMapper(),mock(AuditService.class),null);
    var actor=new AppUser(); actor.setId(1L); actor.setRole(Role.SELLER);
    var owner=new AppUser(); owner.setId(2L);
    var asset=new ProductImageAsset(); asset.setOwner(owner);
    when(users.findByEmailIgnoreCase("seller@test")).thenReturn(Optional.of(actor));
    when(assets.findById(7L)).thenReturn(Optional.of(asset));
    var auth=new UsernamePasswordAuthenticationToken("seller@test","unused");
    assertThrows(SecurityException.class,()->service.original(auth,7L));
    assertThrows(SecurityException.class,()->service.confirm(auth,7L,new ConfirmRequest(9L,true)));
    verifyNoInteractions(files);
  }
}
