package JOO.jooshop.admin.products.service;

import JOO.jooshop.admin.products.model.AdminProductRequestDto;
import JOO.jooshop.admin.products.model.AdminProductResponseDto;
import JOO.jooshop.admin.products.repository.AdminProductRepository;
import JOO.jooshop.categorys.entity.Category;
import JOO.jooshop.contentImgs.entity.ContentImages;
import JOO.jooshop.contentImgs.entity.enums.UploadType;
import JOO.jooshop.contentImgs.repository.ContentImagesRepository;
import JOO.jooshop.global.file.FileStorageService;
import JOO.jooshop.product.entity.Product;
import JOO.jooshop.product.entity.ProductColor;
import JOO.jooshop.productManagement.entity.ProductManagement;
import JOO.jooshop.productManagement.entity.enums.Size;
import JOO.jooshop.thumbnail.entity.ProductThumbnail;
import JOO.jooshop.thumbnail.service.ThumbnailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AdminProductService {

    private final AdminProductRepository productRepository;
    private final ThumbnailService thumbnailService;
    private final ContentImagesRepository contentImagesRepository;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public List<AdminProductResponseDto> findAllProduct() {
        return productRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponseDto)
                .toList();
    }

    public AdminProductResponseDto createProduct(
            AdminProductRequestDto dto,
            MultipartFile thumbnail,
            List<MultipartFile> contentImages
    ) {
        dto.normalizeAndValidate();

        Product product = Product.create(
                dto.getProductName(),
                dto.getProductType(),
                dto.getPrice(),
                dto.getProductInfo(),
                dto.getManufacturer(),
                dto.getIsDiscount(),
                dto.getDiscountRate(),
                dto.getIsRecommend()
        );

        if (dto.hasOptionsField()) {
            product.replaceOptions(toProductManagements(dto.getOptions()));
        }

        if (dto.getThumbnailUrl() != null && !dto.getThumbnailUrl().isBlank()) {
            product.addThumbnailPath(dto.getThumbnailUrl());
        }

        if (dto.getContentUrls() != null && !dto.getContentUrls().isEmpty()) {
            for (String contentUrl : dto.getContentUrls()) {
                if (contentUrl != null && !contentUrl.isBlank()) {
                    product.addContentImagePath(contentUrl, UploadType.PRODUCT);
                }
            }
        }

        if (thumbnail != null && !thumbnail.isEmpty()) {
            String thumbnailPath = thumbnailService.store(thumbnail);
            product.clearThumbnails();
            product.addThumbnailPath(thumbnailPath);
        }

        if (contentImages != null && !contentImages.isEmpty()) {
            product.clearContentImages();
            for (MultipartFile contentImage : contentImages) {
                if (contentImage != null && !contentImage.isEmpty()) {
                    String contentPath = fileStorageService.storeFile(contentImage);
                    product.addContentImagePath(contentPath, UploadType.PRODUCT);
                }
            }
        }

        Product saved = productRepository.save(product);
        return toResponseDto(saved);
    }

    public AdminProductResponseDto updateProduct(
            Long id,
            AdminProductRequestDto dto,
            MultipartFile thumbnail,
            List<MultipartFile> contentImages
    ) {
        dto.normalizeAndValidate();

        Product product = productRepository.findWithDetailsByProductId(id)
                .orElseThrow(() -> new RuntimeException("상품이 존재하지 않습니다."));

        product.changeBasicInfo(
                dto.getProductName(),
                dto.getProductType(),
                dto.getPrice(),
                dto.getProductInfo(),
                dto.getManufacturer(),
                dto.getIsDiscount(),
                dto.getDiscountRate(),
                dto.getIsRecommend()
        );

        if (dto.hasOptionsField()) {
            if (dto.isOptionsClearRequest()) {
                product.clearOptions();
            } else {
                product.replaceOptions(toProductManagements(dto.getOptions()));
            }
        }

        if (thumbnail != null && !thumbnail.isEmpty()) {
            product.clearThumbnails();
            String thumbnailPath = thumbnailService.store(thumbnail);
            product.addThumbnailPath(thumbnailPath);
        }

        if (contentImages != null) {
            product.clearContentImages();

            for (MultipartFile contentImage : contentImages) {
                if (contentImage != null && !contentImage.isEmpty()) {
                    String contentPath = fileStorageService.storeFile(contentImage);
                    product.addContentImagePath(contentPath, UploadType.PRODUCT);
                }
            }
        }

        return toResponseDto(product);
    }

    public void deleteProduct(Long productId) {
        Product product = productRepository.findWithDetailsByProductId(productId)
                .orElseThrow(() -> new RuntimeException("상품이 존재하지 않습니다."));

        deleteThumbnailFilesBestEffort(product);
        deleteContentImageFilesBestEffort(product);

        productRepository.delete(product);
    }

    private List<ProductManagement> toProductManagements(List<AdminProductRequestDto.ProductManagementDto> options) {
        if (options == null || options.isEmpty()) {
            return List.of();
        }

        return options.stream()
                .map(this::toProductManagement)
                .toList();
    }

    private ProductManagement toProductManagement(AdminProductRequestDto.ProductManagementDto dto) {
        ProductColor color = ProductColor.ofName(dto.getColor());
        Category category = Category.ofName(dto.getCategory());
        Size size = Size.valueOf(dto.getSize());

        return ProductManagement.create(
                color,
                category,
                dto.getGender(),
                size,
                dto.getStock() == null ? 0L : dto.getStock()
        );
    }

    private AdminProductResponseDto toResponseDto(Product product) {
        String thumbnailUrl = product.getProductThumbnails().stream()
                .findFirst()
                .map(ProductThumbnail::getImagePath)
                .orElse(null);

        return AdminProductResponseDto.from(product, thumbnailUrl);
    }

    private void deleteThumbnailFilesBestEffort(Product product) {
        product.getProductThumbnails().forEach(thumbnail -> {
            String path = thumbnail.getImagePath();
            deleteFileIfLocal(path);
        });
    }

    private void deleteContentImageFilesBestEffort(Product product) {
        List<String> failedDeletes = new ArrayList<>();

        for (ContentImages image : product.getContentImages()) {
            String path = image.getImagePath();
            try {
                deleteFileIfLocal(path);
            } catch (Exception e) {
                failedDeletes.add(path);
                log.error("상세 이미지 파일 삭제 실패: path={}", path, e);
            }
        }

        if (!failedDeletes.isEmpty()) {
            log.warn("[AdminProduct] content image file delete failures. productId={}, count={}, paths={}",
                    product.getProductId(), failedDeletes.size(), failedDeletes);
        }
    }

    private void deleteFileIfLocal(String path) {
        if (path == null || path.isBlank()) return;

        String trimmed = path.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return;
        }

        fileStorageService.deleteFile(trimmed);
    }
}