package com.homepedia.api.mapper;

import com.homepedia.common.transaction.RealEstateTransaction;
import com.homepedia.common.transaction.TransactionDetail;
import com.homepedia.common.transaction.TransactionStats;
import com.homepedia.common.transaction.TransactionSummary;
import java.math.BigDecimal;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

@Mapper
public interface TransactionMapper {

	TransactionMapper INSTANCE = Mappers.getMapper(TransactionMapper.class);

	@Mapping(source = "city.name", target = "cityName")
	@Mapping(source = "city.inseeCode", target = "cityInseeCode")
	TransactionSummary convertToSummary(RealEstateTransaction transaction);

	List<TransactionSummary> convertToSummaryList(List<RealEstateTransaction> transactions);

	@Mapping(source = "city.name", target = "cityName")
	@Mapping(source = "city.inseeCode", target = "cityInseeCode")
	@Mapping(source = "city.department.code", target = "departmentCode")
	@Mapping(target = "pricePerSqm", source = ".", qualifiedByName = "computePricePerSqm")
	TransactionDetail convertToDetail(RealEstateTransaction transaction);

	@Named("computePricePerSqm")
	default Double computePricePerSqm(RealEstateTransaction tx) {
		if (tx.getPropertyValue() == null || tx.getBuiltSurface() == null || tx.getBuiltSurface() <= 0) {
			return null;
		}
		return tx.getPropertyValue().doubleValue() / tx.getBuiltSurface();
	}

	default TransactionStats emptyStats() {
		return new TransactionStats(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0.0, 0.0);
	}
}
