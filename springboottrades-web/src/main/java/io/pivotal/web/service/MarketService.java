package io.pivotal.web.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.pivotal.web.domain.CompanyInfo;
import io.pivotal.web.domain.MarketSummary;
import io.pivotal.web.domain.Order;
import io.pivotal.web.domain.Quote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.util.UriInfo;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@EnableScheduling
public class MarketService {
	private static final Logger logger = LoggerFactory
			.getLogger(MarketService.class);
	
	@Autowired @Qualifier("quoteService") UriInfo quoteService;
	@Autowired @Qualifier("portfolioService") UriInfo portfolioService;
	
	@Autowired
	private RestTemplate restTemplate;
	
	private static List<String> symbolsIT = Arrays.asList("EMC", "ORCL", "IBM");
	private static List<String> symbolsFS = Arrays.asList("JPM", "C", "MS");
	
	private MarketSummary summary = new MarketSummary();
	
	public MarketSummary getMarketSummary() {
		logger.debug("Retrieving Market Summary");
		
		return summary;
	}
	
	private Quote getQuote(String symbol) {
		logger.debug("Fetching quote: " + symbol);
		Quote quote = restTemplate.getForObject(quoteService.getUri().toString()+"/quote/{symbol}", Quote.class, symbol);
		return quote;
	}
	
	private List<CompanyInfo> getCompanies(String name) {
		logger.debug("Fetching companies with name or symbol matching: " + name);
		CompanyInfo[] infos = restTemplate.getForObject(quoteService.getUri().toString()+"/company/{name}", CompanyInfo[].class, name);
		return Arrays.asList(infos);
	}
	
	public List<Quote> getQuotes(String companyName) {
		logger.debug("Fetching quotes for companies that have: " + companyName + " in name or symbol");
		List<CompanyInfo> companies = getCompanies(companyName);
		List<Quote> quotes = companies.parallelStream().map(n -> getQuote(n.getSymbol())).collect(Collectors.toList());
		return quotes;
	}
	
	public String sendOrder(Order order ){
		logger.debug("send order: " + order);
		
		//TODO: check result of http request to ensure its ok.
		
		ResponseEntity<String>  result = restTemplate.postForEntity(portfolioService.getUri().toString()+"/portfolio/{accountId}", order, String.class, order.getAccountId());
		if (result.getStatusCode() == HttpStatus.BAD_REQUEST) {
			return "Could not save order";
		}
		
		return result.getBody();
	}
	//TODO: prime location for a redis/gemfire caching service!
	@Scheduled(fixedRate = 5000000)
	private void retrieveMarketSummary() {
		logger.debug("Scheduled retrieval of Market Summary");
		List<Quote> quotesIT = symbolsIT.parallelStream().map(symbol -> getQuote(symbol)).collect(Collectors.toList());
		List<Quote> quotesFS = symbolsFS.parallelStream().map(symbol -> getQuote(symbol)).collect(Collectors.toList());
		summary.setTopGainers(quotesIT);
		summary.setTopLosers(quotesFS);
	}
}
