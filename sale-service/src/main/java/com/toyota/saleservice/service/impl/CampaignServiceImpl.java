package com.toyota.saleservice.service.impl;

import com.toyota.saleservice.dao.CampaignRepository;
import com.toyota.saleservice.domain.Campaign;
import com.toyota.saleservice.dto.CampaignDto;
import com.toyota.saleservice.dto.PaginationResponse;
import com.toyota.saleservice.exception.CampaignAlreadyExistsException;
import com.toyota.saleservice.exception.CampaignNotFoundException;
import com.toyota.saleservice.exception.ProductAlreadyInCampaignException;
import com.toyota.saleservice.exception.ProductNotInCampaignException;
import com.toyota.saleservice.service.abstracts.CampaignService;
import com.toyota.saleservice.service.common.MapUtil;
import com.toyota.saleservice.service.common.SortUtil;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CampaignServiceImpl implements CampaignService {

    private final CampaignRepository campaignRepository;
    private final Logger logger = LogManager.getLogger(CampaignService.class);
    private final MapUtil mapUtil;

    @Override
    public PaginationResponse<CampaignDto> getCampaignsFiltered(int page, int size, String name, Double minDiscount, Double maxDiscount, boolean deleted, List<String> sortBy, String sortOrder) {
        logger.info("Getting campaigns with filters");
        Pageable pageable = PageRequest.of(page, size, Sort.by(SortUtil.createSortOrder(sortBy, sortOrder)));
        Page<Campaign> pageResponse = campaignRepository.getCampaignsFiltered(name, minDiscount,
                maxDiscount, deleted, pageable);
        logger.debug("Retrieved {} campaigns.", pageResponse.getContent().size());
        List<CampaignDto> campaignDtos = pageResponse.stream()
                .map(mapUtil::convertCampaignToCampaignDto)
                .collect(Collectors.toList());
        logger.info("Retrieved and converted {} campaigns to dto.", campaignDtos.size());

        return new PaginationResponse<>(campaignDtos, pageResponse);
    }

    @Override
    public CampaignDto addCampaign(CampaignDto campaignDto) {
        logger.info("Adding campaign with name: {}", campaignDto.getName());
        if (campaignRepository.existsByName(campaignDto.getName())) {
            logger.warn("Campaign add failed due to existing campaign with name: {}", campaignDto.getName());
            throw new CampaignAlreadyExistsException("Campaign with this name already exists! " + campaignDto.getName());
        }

        Campaign campaign = mapUtil.convertCampaignDtoToCampaign(campaignDto);
        Campaign saved = campaignRepository.save(campaign);
        logger.info("Campaign with name {} added successfully.", campaignDto.getName());

        return mapUtil.convertCampaignToCampaignDto(saved);
    }

    @Override
    public CampaignDto updateCampaign(Long id, CampaignDto campaignDto) {
        logger.info("Updating campaign with id: {}", id);

        Optional<Campaign> optionalCampaign = campaignRepository.findById(id);
        if (optionalCampaign.isPresent()) {
            Campaign existingCampaign = optionalCampaign.get();

            if (campaignDto.getName() == null) {
                campaignDto.setName(existingCampaign.getName());
            } else if (campaignRepository.existsByNameAndIdNot(campaignDto.getName(), id)) {
                throw new CampaignAlreadyExistsException("Campaign with this name already exists! " + campaignDto.getName());
            }

            Campaign updatedCampaign = mapUtil.convertCampaignDtoToCampaign(campaignDto);
            updatedCampaign.setId(existingCampaign.getId());
            updatedCampaign.setDeleted(existingCampaign.isDeleted());
            updatedCampaign.setProductIds(existingCampaign.getProductIds());

            Campaign savedCampaign = campaignRepository.save(updatedCampaign);
            logger.info("Campaign with id {} updated successfully.", id);

            return mapUtil.convertCampaignToCampaignDto(savedCampaign);
        } else {
            logger.error("Campaign not found with id: {}", id);
            throw new CampaignNotFoundException("Campaign not found with id: " + id);
        }
    }

    @Override
    public CampaignDto deleteCampaign(Long id) {
        logger.info("Deleting campaign with id: {}", id);
        Campaign campaign = campaignRepository.findById(id).orElse(null);
        if (campaign == null) {
            logger.warn("Campaign delete failed due to non-existent campaign with id: {}", id);
            throw new CampaignNotFoundException("Campaign with id " + id + " not found!");
        }
        campaign.setDeleted(true);
        Campaign saved = campaignRepository.save(campaign);
        logger.info("Campaign with id {} deleted successfully.", id);
        return mapUtil.convertCampaignToCampaignDto(saved);
    }

    @Override
    public Optional<Long> getDiscountForProduct(Long productId) {
        logger.info("Getting discount for product with ID: {}", productId);

        List<Campaign> activeCampaigns = campaignRepository.findActiveCampaignsByProductIdNative(productId);
        logger.info("Active campaigns for product ID {}: {}", productId, activeCampaigns);
        if (activeCampaigns.isEmpty()) {
            logger.info("No active campaigns found for product ID: {}", productId);
            return Optional.empty();
        } else {
            Long maxDiscount = activeCampaigns.stream()
                    .map(Campaign::getDiscountPercentage)
                    .max(Long::compareTo)
                    .orElse(null);

            logger.info("Max discount for product ID {} is {}", productId, maxDiscount);
            return Optional.ofNullable(maxDiscount);
        }
    }

    @Override
    public CampaignDto addProductsToCampaign(Long campaignId, List<Long> productIds) {
        logger.info("Adding products to campaign with ID: {}", campaignId);

        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new CampaignNotFoundException("Campaign not found with id: " + campaignId));

        List<Campaign> activeCampaigns = campaignRepository.findActiveCampaigns();

        List<Long> conflictingProductIds = new ArrayList<>();

        for (Long productId : productIds) {
            boolean isConflicting = activeCampaigns.stream()
                    .anyMatch(activeCampaign ->
                            !activeCampaign.getId().equals(campaign.getId()) &&
                                    activeCampaign.getProductIds().contains(productId));

            if (isConflicting) {
                conflictingProductIds.add(productId);
            }
        }

        if (!conflictingProductIds.isEmpty()) {
            logger.warn("Some products are already in another active campaign: {}", conflictingProductIds);
            throw new ProductAlreadyInCampaignException("Products already in another campaign: " + conflictingProductIds);
        }

        List<Long> existingProductIds = campaign.getProductIds();
        productIds.forEach(productId -> {
            if (!existingProductIds.contains(productId)) {
                existingProductIds.add(productId);
            }
        });

        campaign.setProductIds(existingProductIds);
        Campaign savedCampaign = campaignRepository.save(campaign);
        logger.info("Products added to campaign with ID: {}", campaignId);

        return mapUtil.convertCampaignToCampaignDto(savedCampaign);
    }

    @Override
    public CampaignDto removeProductsFromCampaign(Long campaignId, List<Long> productIds) {
        logger.info("Removing products from campaign with ID: {}", campaignId);

        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new CampaignNotFoundException("Campaign not found with id: " + campaignId));

        List<Long> existingProductIds = campaign.getProductIds();

        if (existingProductIds == null || existingProductIds.isEmpty()) {
            logger.warn("No products found in campaign with ID: {}", campaignId);
            throw new ProductNotInCampaignException("No products to remove in campaign with id: " + campaignId);
        }

        List<Long> notInCampaignProductIds = new ArrayList<>();

        for (Long productId : productIds) {
            if (existingProductIds.contains(productId)) {
                existingProductIds.remove(productId);
            } else {
                notInCampaignProductIds.add(productId);
            }
        }

        if (!notInCampaignProductIds.isEmpty()) {
            logger.warn("Some products were not found in the campaign: {}", notInCampaignProductIds);
            throw new ProductNotInCampaignException("Products not found in campaign: " + notInCampaignProductIds);
        }

        campaign.setProductIds(existingProductIds);

        Campaign savedCampaign = campaignRepository.save(campaign);

        logger.info("Products removed from campaign with ID: {}", campaignId);

        return mapUtil.convertCampaignToCampaignDto(savedCampaign);
    }

    @Override
    public CampaignDto removeAllProductsFromCampaign(Long campaignId) {
        logger.info("Removing all products from campaign with ID: {}", campaignId);

        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new CampaignNotFoundException("Campaign not found with id: " + campaignId));

        List<Long> existingProductIds = campaign.getProductIds();

        if (existingProductIds == null || existingProductIds.isEmpty()) {
            logger.warn("No products to remove in campaign with ID: {}", campaignId);
            throw new ProductNotInCampaignException("No products to remove in campaign with id: " + campaignId);
        }

        existingProductIds.clear();
        campaign.setProductIds(existingProductIds);

        Campaign savedCampaign = campaignRepository.save(campaign);

        logger.info("All products removed from campaign with ID: {}", campaignId);

        return mapUtil.convertCampaignToCampaignDto(savedCampaign);
    }

    @Override
    public String getCampaignNameForProduct(Long productId) {
        logger.info("Getting campaign name for product with ID: {}", productId);

        List<Campaign> activeCampaigns = campaignRepository.findActiveCampaignsByProductIdNative(productId);

        if (activeCampaigns.isEmpty()) {
            logger.info("No active campaigns found for product ID: {}", productId);
            return null;
        } else {
            String campaignName = activeCampaigns.get(0).getName();
            logger.info("Campaign name for product ID {} is {}", productId, campaignName);
            return campaignName;
        }
    }
}
