package org.yarr.merlionapi2.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.RateLimiter;
import https.api_merlion_com.dl.mlservice2.ArrayOfItemsResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.yarr.merlionapi2.MLPortProvider;
import org.yarr.merlionapi2.model.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Service
public class CategoryService
{
    private final static Logger log = LoggerFactory.getLogger(CategoryService.class);
    private final MLPortProvider portProvider;

    private final Cache<String, Category> categoryCache = CacheBuilder
            .newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();

    private final Cache<String, Stock> stockCache = CacheBuilder
            .newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    @Autowired
    public CategoryService(MLPortProvider portProvider) {
        this.portProvider = portProvider;
    }

    public Category category(CatalogNode catalog) {
        try
        {
            return categoryCache.get(catalog.id(), new ItemsRetriever(catalog.id(), portProvider));
        } catch (ExecutionException e) {
            log.error("Got exception while retrieving list of items of category {}", catalog);
            return new Category(new HashMap<>());
        }
    }

    public Stock stock(CatalogNode catalog) {
        try
        {
            return stockCache.get(catalog.id(), new ItemsStockRetriever(catalog.id(), portProvider));
        } catch (ExecutionException e) {
            log.error("Got exception while retrieving prices and quantities for category {}", catalog);
            return new Stock(new HashMap<>());
        }
    }
    private static class ItemsRetriever implements Callable<Category>{
        private static final RateLimiter getItemsLimiter = RateLimiter.create(3.0);
        private final MLPortProvider portProvider;
        private final String catId;
        public ItemsRetriever(String catId, MLPortProvider portProvider) {
            this.portProvider = portProvider;
            this.catId = catId;
        }

        @Override
        public Category call() throws Exception
        {
            return new Category(retrieve());
        }

        private Map<String, Item> retrieve()
        {
            double throttle = getItemsLimiter.acquire();
            log.debug("Waited {} seconds for rate limit", throttle);
            ArrayOfItemsResult result = portProvider.get().getItems(catId, "", "1", 0, 10000);
            return new HashMap<>();
        }
    }

    private static class ItemsStockRetriever implements Callable<Stock>{
        private static final RateLimiter getItemsAvailLimiter = RateLimiter.create(5.0);
        private MLPortProvider portProvider;
        private String catId;

        public ItemsStockRetriever(String catId, MLPortProvider portProvider) {
            this.portProvider = portProvider;
            this.catId = catId;
        }

        @Override
        public Stock call() throws Exception
        {
            return new Stock(retrieve());
        }

        private Map<String, StockItem> retrieve()
        {
            double throttle = getItemsAvailLimiter.acquire();
            log.debug("Waited {} seconds for rate limit", throttle);

            return new HashMap<>();
        }
    }

}