// package com.trip.noting.review;
//
// public class PriceStrategyFactory {
//     public static PriceCalculationStrategy getStrategy(User user) {
//         // TODO 问题七 工厂模式对应的产物 两个策略可以使用单例模式，没必要每次创建新的对象
//         // TODO 问题八 如果价格计算使用单例模式就没必要使用工厂模式 可以使用策略模式
//         if (user.isVip()) {
//             return new VipPriceStrategy();
//         }
//         return new RegularPriceStrategy();
//     }
// }