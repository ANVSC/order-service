package com.sumendra.orderservice.service;

import com.sumendra.orderservice.model.OrderRequest;
import com.sumendra.orderservice.model.OrderResponse;

public interface OrderService {
    public long placeOrder(OrderRequest request) ;

    OrderResponse getOrderDetails(long orderId);
}
