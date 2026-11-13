// package com.trip.noting.review;
//
// import java.math.BigDecimal;
//
// public class VipPriceStrategy implements PriceCalculationStrategy {
//     private static final BigDecimal VIP_DISCOUNT = new BigDecimal("0.9");
//
//     @Override
//     public BigDecimal calculate(Order order) {
//         return order.getItems().stream()
//             .map(item -> item.getPrice().multiply(
//                 BigDecimal.valueOf(item.getQuantity())))
//             .reduce(BigDecimal.ZERO, BigDecimal::add)
//             .multiply(VIP_DISCOUNT);
//     }
// }