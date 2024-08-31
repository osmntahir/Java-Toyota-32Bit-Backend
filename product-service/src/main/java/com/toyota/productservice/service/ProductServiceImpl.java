package com.toyota.productservice.service;

import com.toyota.productservice.Mapper.ProductMapper;
import com.toyota.productservice.dao.ProductRepository;
import com.toyota.productservice.domain.Product;
import com.toyota.productservice.dto.ProductDTO;
import jakarta.persistence.EntityNotFoundException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Optional;

@Service
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
   private final Logger logger = LogManager.getLogger(Product.class);

    @Autowired
    private WebClient.Builder webClientBuilder;

    @Autowired
    public ProductServiceImpl(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }
    /**
     * Creates a new product based on the provided ProductDto.
     *
     * @param productDto the ProductDto containing information about the product to be created
     * @return the ProductDto of the newly created product
     * @throws IllegalArgumentException if the name or price of the productDto is null
     */
    @Override
    public ProductDTO createProduct(ProductDTO productDto) {

        if (productDto.getName() != null && productDto.getPrice() != 0.0) {
            Product product = ProductMapper.mapToEntity(productDto);
            Product savedProduct = productRepository.save(product);
            logger.info("Product {} is created", product.getName());
            return ProductMapper.mapToDto(savedProduct);
        } else {
            throw new IllegalArgumentException("Name or price cannot be null");
        }
    }

    /**
     * Retrieves a page of products with optional filtering, sorting, and pagination.
     *
     * @param page           the page number (default: 0)
     * @param size           the size of the page (default: 5)
     * @param name           the name of the product to filter by (optional)
     * @param minPrice       the minimum price of the product to filter by (optional, default: 0)
     * @param maxPrice       the maximum price of the product to filter by (optional, default: Double.MAX_VALUE)
     * @param isActive       the active status of the product to filter by (default: true)
     * @param sortBy         the field to sort by (default: "name")
     * @param sortDirection  the sort direction, either "ASC" (ascending) or "DESC" (descending) (default: "ASC")
     * @return               a page of ProductDto objects representing the products
     */
    @Override
    public Page<ProductDTO> getProducts(int page, int size, String name, Double minPrice, Double maxPrice, boolean isActive, String sortBy, String sortDirection) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(createSortOrder(sortBy, sortDirection)));
        Page<Product> products = productRepository.getProductsFiltered(name, minPrice, maxPrice, isActive, pageable);
        logger.info("Fetched products. Page: {}, Size: {}, Sorted By: {}, Total Pages: {}, Total Elements: {}",
                products.getPageable().getPageNumber(), products.getNumberOfElements(), products.getPageable().getSort(),
                products.getTotalPages(), products.getTotalElements());
        return products.map(ProductMapper::mapToDto);
    }

    /**
     * Creates a Sort.Order object based on the given sortBy and sortDirection parameters.
     *
     * @param sortBy        the field to sort by
     * @param direction     the sort direction, either "ASC" or "DESC"
     * @return              a Sort.Order object representing the sort order
     */
    private Sort.Order createSortOrder(String sortBy, String direction) {
        if(direction.equalsIgnoreCase("Desc"))
            return new Sort.Order(Sort.Direction.DESC,sortBy);
        else
            return new Sort.Order(Sort.Direction.ASC,sortBy);
    }

    /**
     * Retrieves a product by its ID.
     *
     * @param id the ID of the product to retrieve
     * @return the ProductDto object representing the product
     * @throws EntityNotFoundException if no product is found with the given ID
     */
    @Override
    public ProductDTO getProductById(Long id) {
        Optional<Product> optionalProduct = productRepository.findById(id);
        if (optionalProduct.isPresent()) {
            ProductDTO productDto = ProductMapper.mapToDto(optionalProduct.get());
            logger.info("Retrieved product with id: {}", id);
            return productDto;
        } else {
            logger.error("Product not found with id: {}", id);
            throw new EntityNotFoundException("Product not found with id: " + id);
        }
    }



    /**
     * Updates an existing product with the provided ID and ProductDto.
     *
     * @param id          the ID of the product to update
     * @param productDto  the ProductDto containing updated information
     * @return            the updated ProductDto object
     * @throws IllegalArgumentException  if attempting to update a product with inactive status
     * @throws EntityNotFoundException   if no product is found with the given ID
     */
    @Override
    public ProductDTO updateProduct(Long id, ProductDTO productDto) {
        Optional<Product> optionalProduct = productRepository.findById(id);
        if (optionalProduct.isPresent()) {
            Product existingProduct = optionalProduct.get();
            if (productDto.isActive()) {
                Product updatedProduct = ProductMapper.mapToEntity(productDto);
                updatedProduct.setId(existingProduct.getId());
                updatedProduct.setActive(true);
                logger.info("Product with id {} is updated", id);
                return ProductMapper.mapToDto(productRepository.save(updatedProduct));
            } else {
                logger.warn("Attempted to update product with inactive status");
                throw new IllegalArgumentException("Cannot update product with inactive status");
            }
        } else {
            logger.error("Product not found with id: {}", id);
            throw new EntityNotFoundException("Product not found with id: " + id);
        }
    }

    /**
     * Soft deletes a product with the given ID.
     *
     * @param id the ID of the product to soft delete
     * @throws EntityNotFoundException if no product is found with the given ID
     */
    @Override
    public void softDeleteProduct(Long id) {
        Optional<Product> optionalProduct = productRepository.findById(id);
        if (optionalProduct.isPresent()) {
            Product product = optionalProduct.get();
            product.setActive(false); // Soft delete
            productRepository.save(product);
            logger.info("Product with id {} soft deleted", id);
        } else {
            logger.warn("Attempted to soft delete product with id {} but not found", id);
            throw new EntityNotFoundException("Product not found with id: " + id);
        }
    }
}
