package com.sumendra.orderservice.service;

import com.sumendra.orderservice.entity.Order;
import com.sumendra.orderservice.exception.CustomException;
import com.sumendra.orderservice.external.client.PaymentService;
import com.sumendra.orderservice.external.client.ProductService;
import com.sumendra.orderservice.external.request.PaymentRequest;
import com.sumendra.orderservice.model.OrderRequest;
import com.sumendra.orderservice.model.OrderResponse;
import com.sumendra.orderservice.model.PaymentResponse;
import com.sumendra.orderservice.model.ProductResponse;
import com.sumendra.orderservice.repository.OrderRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

@Service
@Log4j2
public class OrderServiceImpl implements OrderService{

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductService  productService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private RestTemplate restTemplate;

    @Override
    public long placeOrder(OrderRequest orderRequest) {

        /*
        1. DONE - Order Entity - Save the order details in Order details table
        2. DoNE - Call product service and check if there is enough stock available if yes, block the products(reduce the quantity)
        3. DONe - Call payment service - if payment is success return complete .
        4. DONe - Call payment service - if payment is fails then update order status as FAILED .
        5. NOT_DONE - Call payment service - if payment is fails then revert back product quantity. .

         */
        log.info("Placing order request : {}",orderRequest);

        productService.reduceQuantity(orderRequest.getProductId(),orderRequest.getQuantity());

        log.info("Reducing product quantity is done - creating order with status CREATED : {}",orderRequest);

        Order order =  Order.builder()
                .amount(orderRequest.getTotalAmount())
                .orderStatus("CREATED")
                .productId(orderRequest.getProductId())
                .orderDate(Instant.now())
                .quantity(orderRequest.getQuantity())
                .build();
        order = orderRepository.save(order);
        log.info("Calling payment service to complete the payment");
        PaymentRequest paymentRequest = PaymentRequest.builder()
                .orderId(order.getId())
                .paymentMode(orderRequest.getPaymentMode())
                .amount(orderRequest.getTotalAmount())
                .build();
        String orderStatus = null;
        try{
            paymentService.doPayment(paymentRequest);
            log.info("Payment Done - Changing order status to Placed");
            orderStatus = "PAYMENT_SUCCESS";
        }
        catch (Exception e)
        {
            log.error("Error occured in payment - Payment Failed");
            orderStatus = "PAYMENT_FAILED";
        }
        order.setOrderStatus(orderStatus);
        orderRepository.save(order);
        log.info("Order placed with successfully with order id : {}",order.getId());

        return order.getId();
    }

    @Override
    public OrderResponse getOrderDetails(long orderId) {
        log.info("Get Order details for the order id {}",orderId);
        Order order
                 = orderRepository.findById(orderId)
                .orElseThrow(()-> new CustomException("Order not found with the given Id","NOT_FOUND",404));

        log.info("Invoking product service to get product details wrt given order id {}",orderId);
        ProductResponse productResponse = restTemplate.getForObject("http://PRODUCT-SERVICE/product/"+order.getProductId(),ProductResponse.class);

        OrderResponse.ProductDetails productDetails = OrderResponse.ProductDetails.builder()
                .productName(productResponse.getProductName())
                .productId(productResponse.getProductId())
                .price(productResponse.getPrice())
                .quantity(productResponse.getQuantity())
                .build();

        log.info("Invoking payment service to get product details wrt given order id {}",orderId);
        PaymentResponse paymentResponse = restTemplate.getForObject("http://PAYMENT-SERVICE/payment/order/"+order.getId(),PaymentResponse.class);
        OrderResponse.PaymentDetails paymentDetails = OrderResponse.PaymentDetails.builder()
                .paymentDate(paymentResponse.getPaymentDate())
                .paymentId(paymentResponse.getPaymentId())
                .paymentMode(paymentResponse.getPaymentMode())
                .amount(paymentResponse.getAmount())
                .orderId(paymentResponse.getOrderId())
                .status(paymentResponse.getStatus())
                .build();

        OrderResponse orderResponse = OrderResponse.builder()
                .amount(order.getAmount())
                .orderDate(order.getOrderDate())
                .orderId(order.getId())
                .orderStatus(order.getOrderStatus())
                .productDetails(productDetails)
                .paymentDetails(paymentDetails)
                .build();

        return orderResponse;
    }
}
