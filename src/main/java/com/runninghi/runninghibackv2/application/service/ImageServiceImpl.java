package com.runninghi.runninghibackv2.application.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.runninghi.runninghibackv2.application.dto.image.response.ImageTarget;
import com.runninghi.runninghibackv2.common.exception.custom.CustomEntityNotFoundException;
import com.runninghi.runninghibackv2.common.utils.S3StorageUtils;
import com.runninghi.runninghibackv2.domain.entity.Image;
import com.runninghi.runninghibackv2.domain.repository.ImageRepository;
import com.runninghi.runninghibackv2.domain.service.ImageChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imgscalr.Scalr;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;


@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ImageServiceImpl implements ImageService {

    private final ImageChecker imageChecker;
    private final ImageRepository imageRepository;

    private final S3StorageUtils s3StorageUtils;

    private final static int IMAGE_RESIZE_TARGET_WIDTH = 650;

    private final static String ENTITY_NOT_FOUND_MESSAGE = "DB에 존재하지 않는 이미지입니다.";

    // 저장 경로 - image / memberNo / UUID + 업로드 시간.jpg
    // 이미지 업로드 시 미리 보기만! -> post 생성 시 이미지 업로드 방식으로 할 지 => DB url

    private Image getImageByImageUrl(String imageUrl) {
        Image image = imageRepository.findImageByImageUrl(imageUrl)
                .orElseThrow(() -> {
                    log.error("DB에 존재하지 않는 이미지입니다. imageUrl: {}", imageUrl);
                    return new CustomEntityNotFoundException(ENTITY_NOT_FOUND_MESSAGE, imageUrl);
                });
        log.info("성공적으로 이미지를 조회하였습니다. imageUrl: {}", imageUrl);
        return image;
    }

    private Image getImageByTargetNo(Long targetNo) {
        Image image = imageRepository.findImageByTargetNo(targetNo)
                .orElseThrow(() -> {
                    log.error("{}번이 할당된 이미지는 DB에 존재하지 않습니다.", targetNo);
                    return new CustomEntityNotFoundException(ENTITY_NOT_FOUND_MESSAGE, targetNo);
                });
        log.info("성공적으로 이미지를 조회하였습니다. targetNo: {}", targetNo);
        return image;
    }

    private Image getImageByImageTargetAndTargetNo(ImageTarget target, Long targetNo) {
        Image image = imageRepository.findImageByTargetNoAndImageTarget(targetNo, target)
                .orElseThrow(() -> {
                    log.error("DB에 존재하지 않는 이미지입니다. target: {}, targetNo: {}", target, targetNo);
                    return new CustomEntityNotFoundException(ENTITY_NOT_FOUND_MESSAGE);
                });
        log.info("성공적으로 이미지를 조회하였습니다. targetNo: {}", targetNo);
        return image;
    }

    /**
     * 이미지를 단순히 S3에 업로드하고 반환된 url을 String 형태로 반환해주는 메서드입니다.
     *
     * @param fileList MultipartFile List
     * @param memberNo 회원 식별 값. s3 업로드 시에 경로에 사용.(경로 통일)
     * @param dirName  S3에 업로드할 때 지정되는 Path
     * @return 이미지 url 리스트
     * @throws IOException file -> byte[] 변환 과정에서 IO 예외 발생 처리
     */
    @Override
    public List<String> uploadImageList(List<MultipartFile> fileList, Long memberNo, String dirName) throws IOException {

        imageChecker.checkMaxLength(fileList);

        List<String> imageUrlList = new ArrayList<>();
        for (MultipartFile file : fileList) {
            imageUrlList.add(uploadImage(file, memberNo, dirName));
        }
        return imageUrlList;
    }

    /**
     * 이미지 한 장을 단순히 S3에 업로드하고 반환된 url을 String 형태로 반환해주는 메서드입니다.
     *
     * @param multipartFile MultipartFile
     * @param memberNo      회원 식별 값. s3 업로드 시에 경로에 사용.(경로 통일)
     * @param dirName       S3에 업로드할 때 지정되는 Path
     * @return 이미지 url
     * @throws IOException file -> byte[] 변환 과정에서 IO 예외 발생 처리
     */
    @Override
    public String uploadImage(MultipartFile multipartFile, Long memberNo, String dirName) throws IOException {

        // 이미지 파일 유효성 검증
        imageChecker.checkImageFile(multipartFile.getOriginalFilename());

        dirName += memberNo;
        String key = s3StorageUtils.buildKey(multipartFile, dirName);

        Map<String, String> metadata = extractMetadata(multipartFile);
        byte[] processedImage;

        String fileExtension = imageChecker.getFileExtension(Objects.requireNonNull(multipartFile.getOriginalFilename()));
        if (imageChecker.isHeifOrHeic(fileExtension)) {
            processedImage = multipartFile.getBytes();
        } else {
            processedImage = resizeImage(multipartFile);
        }

        return s3StorageUtils.uploadFileWithMetadata(processedImage, key, metadata);
    }

    /**
     * 이미지의 메타데이터를 추출합니다. 다만, 최소한의 정보로 이미지의 방향만을 추출합니다.
     * @param multipartFile
     * @return
     * @throws IOException
     */
    private Map<String, String> extractMetadata(MultipartFile multipartFile) throws IOException {
        Map<String, String> metadata = new HashMap<>();
        try {
            Metadata imageMetadata = ImageMetadataReader.readMetadata(multipartFile.getInputStream());
            ExifIFD0Directory exifIFD0Directory = imageMetadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (exifIFD0Directory != null && exifIFD0Directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                int orientation = exifIFD0Directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
                metadata.put("Orientation", String.valueOf(orientation));
            }
        } catch (ImageProcessingException | MetadataException e) {
            log.warn("메타데이터 추출 중 오류 발생", e);
        }
        return metadata;
    }

    @Override
    public void saveImageList(List<String> imageUrlList) {
        List<Image> imageList = new ArrayList<>();

        for (String imageUrl : imageUrlList) {
            imageList.add(
                    Image.builder()
                            .imageUrl(imageUrl)
                            .build()
            );
        }

        imageRepository.saveAll(imageList);
        log.info("이미지 리스트가 DB에 추가되었습니다.");
    }

    @Override
    public void saveImage(String imageUrl) {

        imageRepository.save(Image.builder()
                .imageUrl(imageUrl)
                .build()
        );
        log.info("이미지가 DB에 추가되었습니다.");
    }

    @Override
    public void saveTargetNo(List<String> imageUrlList, ImageTarget imageTarget, Long targetNo) {

        for (String imageUrl : imageUrlList) {
            saveTargetNo(imageUrl, imageTarget, targetNo);
        }
    }

    @Override
    public void saveTargetNo(String imageUrl, ImageTarget imageTarget, Long targetNo) {

        log.info("이미지에 아이디를 할당합니다. imageTarget: {}, targetNo: {}", imageTarget, targetNo);
        String filename = imageChecker.getFileNameFromUrl(imageUrl);
        imageChecker.checkImageFile(filename);

        Image image = getImageByImageUrl(imageUrl);
        image.updateImageTarget(imageTarget);
        image.updateTargetNo(targetNo);
        log.info("{} 번의 이미지가 {} 의 {} 번의 엔테티로 할당되었습니다,", image.getId(), image.getImageTarget(), image.getTargetNo());
    }

    @Override
    public void updateImage(ImageTarget target, Long targetNo, String newImageUrl) {
        Image existingImage = imageRepository.findImageByTargetNoAndImageTarget(targetNo, target)
                .orElse(null);

        // 새 이미지 URL이 제공되었고, 기존 이미지와 다른 경우
        if (!newImageUrl.isBlank() && (existingImage == null || !imageChecker.isSameImage(existingImage.getImageUrl(), newImageUrl))) {
            handleNewImage(existingImage, target, targetNo, newImageUrl);
        }
        // 새 이미지 URL이 비어있는 경우 (이미지 삭제)
        else if (newImageUrl.isBlank() && existingImage != null) {
            handleImageDeletion(existingImage);
        }
        // 변경사항이 없는 경우
        else {
            log.info("이미지 변경 사항이 없습니다. target: {}, targetNo: {}", target, targetNo);
        }
    }

    private void handleNewImage(Image existingImage, ImageTarget target, Long targetNo, String newImageUrl) {
        // 새 이미지 URL에 대한 유효성 검사
        imageChecker.checkImageFile(imageChecker.getFileNameFromUrl(newImageUrl));

        // 기존 이미지가 있다면 스토리지에서 삭제
        if (existingImage != null && !existingImage.getImageUrl().isBlank()) {
            deleteImageFromStorage(existingImage.getImageUrl());
            imageRepository.delete(existingImage);
        }

        // 새 이미지에 대한 targetNo와 imageTarget 할당
        saveTargetNo(newImageUrl, target, targetNo);

        log.info("{} - {}의 이미지가 {}로 변경되었습니다.", target, targetNo, newImageUrl);
    }

    private void handleImageDeletion(Image existingImage) {
        deleteImageFromStorage(existingImage.getImageUrl());
        imageRepository.delete(existingImage);
        log.info("{} 이미지가 삭제되었습니다.", existingImage.getImageUrl());
    }


    @Override
    public byte[] resizeImage(MultipartFile multipartFile) throws IOException {

        log.info("{} 이미지를 리사이징합니다.", multipartFile.getOriginalFilename());
        String fileExtension = imageChecker.getFileExtension(Objects.requireNonNull(multipartFile.getOriginalFilename()));
        log.info("fileExtension: {}", fileExtension);

        BufferedImage originalImage = ImageIO.read(multipartFile.getInputStream());
        if (originalImage == null) {
            throw new IOException("이미지 리사이징 중 오류가 발생하였습니다.");
        }

        originalImage = rotateImageIfRequired(originalImage, multipartFile);

        BufferedImage resizedImage = Scalr.resize(
                originalImage,
                Scalr.Method.QUALITY,
                Scalr.Mode.FIT_TO_WIDTH,
                IMAGE_RESIZE_TARGET_WIDTH,
                Scalr.THRESHOLD_QUALITY_BALANCED
        );

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        boolean success = ImageIO.write(resizedImage, fileExtension, outputStream);

        if (!success) {
            throw new IOException("이미지를 " + fileExtension + " 형식으로 저장할 수 없습니다.");
        }

        log.info("성공적으로 이미지가 리사이징되었습니다.");
        return outputStream.toByteArray();
    }

    private BufferedImage rotateImageIfRequired(BufferedImage image, MultipartFile multipartFile) throws IOException {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(multipartFile.getInputStream());
            Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (directory != null && directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                int orientation = directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
                switch (orientation) {
                    case 3:
                        return Scalr.rotate(image, Scalr.Rotation.CW_180);
                    case 6:
                        return Scalr.rotate(image, Scalr.Rotation.CW_90);
                    case 8:
                        return Scalr.rotate(image, Scalr.Rotation.CW_270);
                }
            }
        } catch (ImageProcessingException | com.drew.metadata.MetadataException e) {
            log.warn("이미지 방향 정보 읽기 실패", e);
        }
        return image;
    }

    @Override
    public void deleteImageList(List<String> imageUrlList) {

        List<Image> existingImages = imageRepository.findByImageUrlIn(imageUrlList);

        List<String> existingUrls = existingImages.stream()
                .map(Image::getImageUrl)
                .toList();

        // S3에서 이미지 삭제
        for (String imageUrl : existingUrls) {
            s3StorageUtils.deleteFile(imageUrl);
        }

        // DB에서 이미지 정보 일괄 삭제
        int deletedCount = imageRepository.deleteAllByImageUrlIn(existingUrls);

        log.info("DB와 Storage 에서 {}개의 이미지가 삭제되었습니다.", deletedCount);
    }

    @Override
    public void deleteImageFromStorage(String imageUrl) {

        s3StorageUtils.deleteFile(imageUrl);
        log.info("{} 이미지가 Storage 에서 이미지가 삭제되었습니다.", imageUrl);
    }

    @Override
    public void deleteImageFromDB(String imageUrl) {
        Image image = getImageByImageUrl(imageUrl);
        imageRepository.delete(image);
        log.info("{} 이미지가 DB에서 삭제되었습니다.", imageUrl);
    }

    @Override
    public void deleteImageFromDBByImageTargetAndTargetNo(ImageTarget target, Long targetNo) {
        Image image = getImageByImageTargetAndTargetNo(target, targetNo);
        imageRepository.delete(image);
        log.info("{} 이미지가 DB에서 삭제되었습니다.", image.getImageUrl());
    }

    @Scheduled(cron = "0 0 1 * * ?") // 매일 새벽 1시에 실행
    public void deleteUnassignedImages() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(7); // 일주일 동안 할당되지 않은 이미지 삭제
        log.info("{}일 이후의 할당되지 않은 사진들을 삭제합니다.", cutoffDate);
        List<String> unassignedImageUrlList = imageRepository.findUnassignedImagesBeforeDate(cutoffDate);

        // S3에서 이미지 삭제
        for (String url : unassignedImageUrlList) {
            s3StorageUtils.deleteFile(url);
        }

        int deletedCount = imageRepository.deleteAllByImageUrlIn(unassignedImageUrlList);
        log.info("DB와 Storage 에서 {}개의 이미지가 삭제되었습니다.", deletedCount);
    }
}
