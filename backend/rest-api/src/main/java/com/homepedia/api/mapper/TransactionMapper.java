package com.homepedia.api.mapper;

import com.homepedia.common.transaction.RealEstateTransaction;
import com.homepedia.common.transaction.TransactionStats;
import com.homepedia.common.transaction.TransactionSummary;
import java.math.BigDecimal;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface TransactionMapper {

	TransactionMapper INSTANCE = Mappers.getMapper(TransactionMapper.class);

	@Mapping(source = "city.name", target = "cityName")
	@Mapping(source = "city.inseeCode", target = "cityInseeCode")
	TransactionSummary convertToSummary(RealEstateTransaction transaction);

	List<TransactionSummary> convertToSummaryList(List<RealEstateTransaction> transactions);

	default TransactionStats emptyStats() {
		return new TransactionStats(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0.0, 0.0);
	}
}
