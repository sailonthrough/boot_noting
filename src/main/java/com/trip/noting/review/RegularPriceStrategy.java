// package com.trip.noting.review;
//
// import java.math.BigDecimal;
// //
// public class RegularPriceStrategy implements PriceCalculationStrategy {
//     @Override
//     public BigDecimal calculate(Order order) {
//         BigDecimal bigDecimal = new BigDecimal();
//         return order.getItems().stream()
//             .map(item -> item.getPrice().multiply(
//                 BigDecimal.valueOf(item.getQuantity())))
//             .reduce(BigDecimal.ZERO, BigDecimal::add);
//     }
// }