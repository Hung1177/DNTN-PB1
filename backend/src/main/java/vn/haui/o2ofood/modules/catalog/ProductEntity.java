package vn.haui.o2ofood.modules.catalog;

import jakarta.persistence.*;

@Entity
@Table(name = "products")
public class ProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sku_prefix", nullable = false, unique = true, length = 40)
    private String skuPrefix;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "category", nullable = false, length = 100)
    private String category;

    @Column(name = "storage_type", nullable = false, length = 30)
    private String storageType;

    @Column(name = "min_shelf_life_days_at_pickup", nullable = false)
    private Integer minShelfLifeDaysAtPickup = 1;

    @Column(name = "dietary_tags", length = 255)
    private String dietaryTags;

    @Column(name = "allergen_tags", length = 255)
    private String allergenTags;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "supplier_name", length = 200)
    private String supplierName;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    public ProductEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSkuPrefix() {
        return skuPrefix;
    }

    public void setSkuPrefix(String skuPrefix) {
        this.skuPrefix = skuPrefix;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getStorageType() {
        return storageType;
    }

    public void setStorageType(String storageType) {
        this.storageType = storageType;
    }

    public Integer getMinShelfLifeDaysAtPickup() {
        return minShelfLifeDaysAtPickup;
    }

    public void setMinShelfLifeDaysAtPickup(Integer minShelfLifeDaysAtPickup) {
        this.minShelfLifeDaysAtPickup = minShelfLifeDaysAtPickup;
    }

    public String getDietaryTags() {
        return dietaryTags;
    }

    public void setDietaryTags(String dietaryTags) {
        this.dietaryTags = dietaryTags;
    }

    public String getAllergenTags() {
        return allergenTags;
    }

    public void setAllergenTags(String allergenTags) {
        this.allergenTags = allergenTags;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
