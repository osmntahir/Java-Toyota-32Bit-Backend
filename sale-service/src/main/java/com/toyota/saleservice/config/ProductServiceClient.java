package com.toyota.saleservice.config;

import com.toyota.saleservice.dto.ProductDTO;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

@Service
public class ProductServiceClient {

    private final RestTemplate restTemplate;

    public ProductServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Optional<ProductDTO> getProductById(Long productId) {
        try {
            System.out.println("Fetching product with ID: " + productId);

            ProductDTO product = restTemplate.getForObject(
                    "http://product-service/product/" + productId, ProductDTO.class);

            if (product == null) {
                System.out.println("Product not found for ID: " + productId);
            } else {
                System.out.println("Product found: " + product);
            }

            return Optional.ofNullable(product);
        } catch (Exception e) {
            System.out.println("Exception occurred while fetching product: " + e.getMessage());
            e.printStackTrace();
            return Optional.empty();
        }
    }

    public Optional<ProductDTO> getProductByIdIncludeInactive(Long productId) {
        try {
            System.out.println("Fetching product with ID: " + productId);

            ProductDTO product = restTemplate.getForObject(
                    "http://product-service/product/getByIdIncludeInactive/" + productId, ProductDTO.class);

            if (product == null) {
                System.out.println("Product not found for ID: " + productId);
            } else {
                System.out.println("Product found: " + product);
            }

            return Optional.ofNullable(product);
        } catch (Exception e) {
            System.out.println("Exception occurred while fetching product: " + e.getMessage());
            e.printStackTrace();
            return Optional.empty();
        }
    }



    public void updateProductInventory(ProductDTO productDTO) {
        try {
            restTemplate.put("http://product-service/product/updateInventory/" + productDTO.getId(), productDTO);
        } catch (Exception e) {
            throw new RuntimeException("Failed to update product inventory", e);
        }
    }

    public ProductDTO createProduct(ProductDTO productDto) {
        return restTemplate.postForObject("http://product-service/product/add", productDto, ProductDTO.class);
    }

    public void deleteProduct(Long productId) {
        try {
            restTemplate.delete("http://product-service/product/delete/" + productId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete product", e);
        }
    }

    public List<ProductDTO> getProductsByIds(List<Long> productIds) {
        return restTemplate.postForObject("http://product-service/product/getByIds", productIds, List.class);
    }
}
