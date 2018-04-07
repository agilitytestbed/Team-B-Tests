package nl.utwente.ing.testsuite;
import static io.restassured.RestAssured.given;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;

import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Test;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;

public class ControllerTest {
	private static String sessionID;
	private static String validCategoryID;
	private static String validTransactionID;
	private static final int INITIAL_TRANSACTIONS = 25;
	private static final int INITIAL_CATEGORIES = 5;
	
	@BeforeClass
	public static void before() {
		RestAssured.basePath = "/api/v1";
		sessionID = 
		given().
		        contentType("application/json").
		when().
		        post("/sessions").
		then().
				contentType(ContentType.JSON).
		extract().
				response().jsonPath().getString("id");
		
		// Add some categories to test with
		JSONObject category = new JSONObject()
											.put("name", "test");
		for (int i = 0; i < INITIAL_CATEGORIES; i++) {
			given().
				header("X-session-ID", sessionID).
				header("Content-Type", "application/JSON").
				body(category.toString()).
			when().
				post("/categories");
		}
		// Get a valid categoryID to work with
		JsonPath categoryJson = 
				given().
						header("X-session-ID", sessionID).
						header("Content-Type", "application/JSON").
				when().
				        get("/categories").
				then().
						contentType(ContentType.JSON).
				extract().
						response().jsonPath();
		// Get the first categoryID
		validCategoryID = categoryJson.getString("id[0]");
		// Add some transactions to work with
		JSONObject transaction = new JSONObject().put("date", LocalDateTime.now())
												.put("amount", 10.0)
												.put("externalIBAN", "testIBAN")
												.put("type", "deposit");
		for (int i = 0; i< INITIAL_TRANSACTIONS; i++) {
			given().
				header("X-session-ID", sessionID).
				header("Content-Type", "application/JSON").
				body(transaction.toString()).
			when().
				post("/transactions");
		}

		
		// Get a valid transaction id to work with
		JsonPath transactionJson = 
			given().
				header("X-session-ID", sessionID).
				header("Content-Type", "application/JSON").
			when().
		        get("/transactions").
			then().
				contentType(ContentType.JSON).
			extract().
				response().jsonPath();
		// Get the first transaction id
		validTransactionID = transactionJson.getString("id[0]");
		
	}
	
	@Test
	public void testGetTransactions() {
		// ---- Headers ----
		// Mismatching session IDs
		given().
			header("Content-Type", "application/JSON").
			header("X-session-ID", Integer.parseInt(sessionID)-1).
		when().
			get("/transactions?session_id=" + sessionID).
		then().
			assertThat().statusCode(401);
		
		// Valid header
		Response headerTransactions = given().
				header("X-session-ID", sessionID).
				header("Content-Type", "application/JSON").
		when().
				get("/transactions");
		
		// Check the response code
		headerTransactions.
			then().
				assertThat().statusCode(200);
		// Valid session id as parameter
		Response parameterTransactions = given().
			header("Content-Type", "application/JSON").
		when().
			get("/transactions?session_id=" + sessionID);
		
		//Check the response code
		parameterTransactions.
		then().
			assertThat().statusCode(200);
		
		// Get the response bodies
		JsonPath headerTransactionsJson = headerTransactions.jsonPath();
		JsonPath parameterTransactionsJson = parameterTransactions.jsonPath();
		
		// Check if the two requests produce the same bodies
		assertEquals(headerTransactionsJson.getList("id"), parameterTransactionsJson.getList("id"));
		assertEquals(headerTransactionsJson.getList("date"), parameterTransactionsJson.getList("date"));
		assertEquals(headerTransactionsJson.getList("externalIBAN"), parameterTransactionsJson.getList("externalIBAN"));
		assertTrue(headerTransactionsJson.getList("amount").equals(parameterTransactionsJson.getList("amount")));
		assertEquals(headerTransactionsJson.getList("category"), parameterTransactionsJson.getList("category"));

		// Check if IDs make sense
		for (Object id: headerTransactions.jsonPath().getList("id")) {
			assertTrue((int)id >= 0);
		}
		
		// Check if dates are in the correct format
		for (Object date: headerTransactions.jsonPath().getList("date")) {
			boolean validDate = true;
			try {
				LocalDateTime.parse((String)date);
			} catch (DateTimeParseException e) {
				validDate = false;
			}
			assertTrue(validDate);
		}
		
		// Check if amounts make sense
		for (Object amount: headerTransactions.jsonPath().getList("amount")) {
			assertTrue((float)amount > 0);
		}
		
		// Check if IBANs are not null
		for (Object iban: headerTransactions.jsonPath().getList("externalIBAN")) {
			assertTrue((String)iban != null);
		}
		
		// Check if the types are correct
		for (Object type: headerTransactions.jsonPath().getList("type")) {
			assertTrue(type.equals("deposit") ||
					type.equals("withdrawal"));
		}
		
		// Invalid header
		given().
			header("X-session-ID", -1).
			header("Content-Type", "application/JSON").
		when().
			get("/transactions").
		then().
			assertThat().statusCode(401);
		// No header
		given().
			header("Content-Type", "application/JSON").
		when().
			get("/transactions").
		then().
			assertThat().statusCode(401);
		// ---- Offset ----
		
		// Get the first transaction id starting with offset 0
		JsonPath transactionJson = 
			given().
				header("X-session-ID", sessionID).
				header("Content-Type", "application/JSON").
				param("offset", 0).
			when().
				get("/transactions").
			then().
				contentType(ContentType.JSON).
			extract().
				response().jsonPath();
		String firstTransactionID = transactionJson.getString("id[0]");
		
		// Get the first transaction id starting with offset 1
		transactionJson = 
			given().
				header("X-session-ID", sessionID).
				header("Content-Type", "application/JSON").
				param("offset", 1).
			when().
				get("/transactions").
			then().
				contentType(ContentType.JSON).
			extract().
				response().jsonPath();
		String secondTransactionID = transactionJson.getString("id[0]");

		// Check if the offset works
		assertTrue(Integer.parseInt(firstTransactionID) + 1 == Integer.parseInt(secondTransactionID));
		
		// Check the lower bound on the offset
		transactionJson = 
		given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
			param("offset", -1).
		when().
			get("/transactions").
		then().
			contentType(ContentType.JSON).
		extract().
			response().jsonPath();
		
		// The offset should be 0
		assertEquals(transactionJson.getString("id[0]"), firstTransactionID);
		// ---- Limit ----
		
		transactionJson = 
			given().
				header("X-session-ID", sessionID).
				header("Content-Type", "application/JSON").
				param("limit", 1).
			when().
				get("/transactions").
			then().
				contentType(ContentType.JSON).
			extract().
				response().jsonPath();
		int nrTransactions = transactionJson.getList("id").size();
		assertTrue(nrTransactions == 1);
		
		transactionJson = 
			given().
				header("X-session-ID", sessionID).
				header("Content-Type", "application/JSON").
				param("limit", 2).
			when().
				get("/transactions").
			then().
				contentType(ContentType.JSON).
			extract().
				response().jsonPath();
			nrTransactions = transactionJson.getList("id").size();
			
			assertTrue(nrTransactions == 2);
		
		// Limit bounds
			// lower bound
			transactionJson = 
			given().
				header("X-session-ID", sessionID).
				header("Content-Type", "application/JSON").
				param("limit", -1).
			when().
				get("/transactions").
			then().
				contentType(ContentType.JSON).
			extract().
				response().jsonPath();
			nrTransactions = transactionJson.getList("id").size();
			
			assertTrue(nrTransactions == 1);
			
		
		// Default values of limit and offset
			transactionJson = 
			given().
				header("X-session-ID", sessionID).
				header("Content-Type", "application/JSON").
			when().
				get("/transactions").
			then().
				contentType(ContentType.JSON).
			extract().
				response().jsonPath();
			nrTransactions = transactionJson.getList("id").size();
					
			assertTrue(nrTransactions == 20);
			assertEquals(transactionJson.getString("id[0]"), firstTransactionID);
		
		// ---- Category ----
		// Make sure the transactions we want to test on have a category
		given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
			body(new JSONObject().put("category_id", validCategoryID).toString()).
		when().
			patch("/transactions/" + validTransactionID + "/category");
		
		given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
			body(new JSONObject().put("category_id", validCategoryID).toString()).
		when().
			patch("/transactions/" + (Integer.parseInt(validTransactionID) +1) + "/category");
		
		transactionJson = 
			given().
				header("X-session-ID", sessionID).
				header("Content-Type", "application/JSON").
				param("category", validCategoryID).
			when().
				get("/transactions").
			then().
				contentType(ContentType.JSON).
			extract().
				response().jsonPath();
		

		
		List<HashMap<String,Integer>> categoryIds = transactionJson.getList("category");
		// We make sure we have enough category ids
		assertTrue(categoryIds.size() >= 2);
		// Test all returned category Ids
		for (HashMap<String,Integer> categoryID : categoryIds) {
			assertTrue(categoryID.get("id") == Integer.parseInt(validCategoryID));
		}
	}
	
	@Test
	public void testPostTransaction() {
		LocalDateTime now = LocalDateTime.now();
		JSONObject transaction = new JSONObject().put("date", now.toString())
												.put("amount", "15.0")
												.put("externalIBAN", "testIBAN")
												.put("type", "deposit");
		// Mismatching session IDs
		given().
			header("Content-Type", "application/JSON").
			header("X-session-ID", Integer.parseInt(sessionID)-1).
			body(transaction.toString()).
		when().
			post("/transactions?session_id=" + sessionID).
		then().
			assertThat().statusCode(401);
		
		// No header
		given().
			header("Content-Type", "application/JSON").
			body(transaction.toString()).
		when().
			post("/transactions").
		then().
			assertThat().statusCode(401);
		
		// Invalid header
		given().
			header("X-session-ID", -1).
			header("Content-Type", "application/JSON").
			body(transaction.toString()).
		when().
			post("/transactions").
		then().
			assertThat().statusCode(401);
		
		// Valid header
		Response headerTransactionResponse = given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
			body(transaction.toString()).
		when().
			post("/transactions");
		
		headerTransactionResponse.
			then().
				assertThat().statusCode(201);
		
		// Valid session id as parameter
		Response parameterTransactionResponse = given().
			header("Content-Type", "application/JSON").
			body(transaction.toString()).
		when().
			post("/transactions?session_id=" + sessionID);
		parameterTransactionResponse.
		then().
			assertThat().statusCode(201);

		JsonPath headerTransactionJson = headerTransactionResponse.jsonPath();
		JsonPath parameterTransactionJson = parameterTransactionResponse.jsonPath();
		
		// Check the headerTransactionJson response
		assertTrue((int)headerTransactionJson.get("id") > 0);
		assertEquals(headerTransactionJson.get("date"), now.toString());
		assertTrue((float)headerTransactionJson.get("amount") == 15.0);
		assertEquals(headerTransactionJson.get("externalIBAN"), "testIBAN");
		assertEquals(headerTransactionJson.get("type"), "deposit");
		
		// Check the parameterTransactionJson response
		assertTrue(headerTransactionJson.getInt("id") + 1 == parameterTransactionJson.getInt("id"));
		assertEquals(parameterTransactionJson.get("date"), now.toString());
		assertTrue((float)parameterTransactionJson.get("amount") == 15.0);
		assertEquals(parameterTransactionJson.get("externalIBAN"), "testIBAN");
		assertEquals(parameterTransactionJson.get("type"), "deposit");
		
		// Invalid input
		// amount = 0
		transaction.put("amount", 0);
		given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
			body(transaction.toString()).
		when().
			post("/transactions").
		then().
			assertThat().statusCode(405);
		// amount is negative
		transaction.put("amount", -15);
		given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
			body(transaction.toString()).
		when().
			post("/transactions").
		then().
			assertThat().statusCode(405);
		
		
		// null externalIBAN
		transaction.
					put("categoryID", validCategoryID).
					remove("externalIBAN");
		given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
			body(transaction.toString()).
		when().
			post("/transactions").
		then().
			assertThat().statusCode(405);
		
		// null date
		transaction.
					put("externalIBAN", "testIBAN").
					remove("date");
		given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
			body(transaction.toString()).
		when().
			post("/transactions").
		then().
			assertThat().statusCode(405);
		
		// invalid date format
		transaction.
		put("externalIBAN", "testIBAN").put("date", "some_random_invalid_date_format");
		given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
			body(transaction.toString()).
		when().
			post("/transactions").
		then().
			assertThat().statusCode(405);
		
		// wrong type of transaction
		transaction.
		put("type", "invalid_type").put("date", now.toString());
		given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
			body(transaction.toString()).
		when().
			post("/transactions").
		then().
			assertThat().statusCode(405);
		
		// no body
		given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
		when().
			post("/transactions").
		then().
			assertThat().statusCode(405);
	}
	
	@Test
	public void testGetTransaction() {
		// ---- Headers ----
		// Mismatching session IDs
		given().
			header("Content-Type", "application/JSON").
			header("X-session-ID", Integer.parseInt(sessionID)-1).
		when().
			get("/transactions/"+ validTransactionID + "?session_id=" + sessionID).
		then().
			assertThat().statusCode(401);
		
		// No header
		given().
			header("Content-Type", "application/JSON").
		when().
			get("/transactions/" + validTransactionID).
		then().
			assertThat().statusCode(401);
		
		// Invalid header
		given().
			header("X-session-ID", -1).
			header("Content-Type", "application/JSON").
		when().
			get("/transactions/" + validTransactionID).
		then().
			assertThat().statusCode(401);
		
		// Valid header
		Response headerTransactionResponse = given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
		when().
			get("/transactions/" + validTransactionID);
		
		
		// Check the response code
		headerTransactionResponse.
		then().
			assertThat().statusCode(200);
		
		// Valid session id as parameter
		Response parameterTransactionResponse = given().
			header("Content-Type", "application/JSON").
		when().
			get("/transactions/" + validTransactionID + "?session_id=" + sessionID);
		
		// Check response code
		parameterTransactionResponse.
		then().
			assertThat().statusCode(200);
		
		
		// Get the response bodies of both
		JsonPath headerTransaction = headerTransactionResponse.jsonPath();
		JsonPath parameterTransaction = parameterTransactionResponse.jsonPath();
		
		// Check if the two GET requests yield the same response
		assertEquals(parameterTransaction.getInt("id"), headerTransaction.getInt("id"));
		assertEquals(parameterTransaction.getString("date"), headerTransaction.getString("date"));
		assertEquals(parameterTransaction.getString("externalIBAN"), headerTransaction.getString("externalIBAN"));
		assertTrue(parameterTransaction.getFloat("amount") == headerTransaction.getFloat("amount"));
		assertEquals(parameterTransaction.getMap("category"), headerTransaction.getMap("category"));
		
		assertEquals(headerTransaction.getInt("id"), Integer.parseInt(validTransactionID));
		// Check if the transaction's date is in the valid datetime format
		boolean validDate = true;
		try {
			LocalDateTime.parse(headerTransaction.getString("date"));
		} catch (DateTimeParseException e) {
			validDate = false;
		}
		assertTrue(validDate);
		// Make sure the amount is positive
		assertTrue(headerTransaction.getFloat("amount") > 0);
		// Check if the eternal IBAN is not null
		assertTrue(headerTransaction.getString("externalIBAN")!= null);
		// Check if the transaction type is valid
		assertTrue(headerTransaction.getString("type").equals("deposit") 
				|| headerTransaction.getString("type").equals("withdrawal"));
		
		
		// ---- Non-existent ID ----
		given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
		when().
			get("/transactions/" + -1).
		then().
			assertThat().statusCode(404);
	}
	
	@Test
	public void testPutTransaction() {
		String now = LocalDateTime.now().toString();
		JSONObject transaction = new JSONObject().put("date", now)
				.put("amount", 213.04)
				.put("externalIBAN", "NL39RABO0300065264")
				.put("type", "deposit");
		// ---- Headers ----
		// Mismatching session IDs
		given().
			header("Content-Type", "application/JSON").
			header("X-session-ID", Integer.parseInt(sessionID)-1).
			body(transaction.toString()).
		when().
			put("/transactions/" + validTransactionID +"?session_id=" + sessionID).
		then().
			assertThat().statusCode(401);
		// No header
		given().
			header("Content-Type", "application/JSON").
			body(transaction.toString()).
		when().
			put("/transactions/" + validTransactionID).
		then().
			assertThat().statusCode(401);
		
		// Invalid header
		given().
			header("X-session-ID", -1).
			header("Content-Type", "application/JSON").
			body(transaction.toString()).
		when().
			put("/transactions/" + validTransactionID).
		then().
			assertThat().statusCode(401);
		
		// Valid header
		Response headerTransactionResponse = given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
			body(transaction.toString()).
		when().
			put("/transactions/" + validTransactionID);
		
		// Check the response code
		headerTransactionResponse.
		then().
			assertThat().statusCode(200);
		
		
		// Check if transaction has changed
		JsonPath transactionJson = headerTransactionResponse.jsonPath();
		
		// Check if each parameter is the same as in the put request
		assertEquals(transactionJson.getInt("id"), Integer.parseInt(validTransactionID));
		assertEquals(transactionJson.get("date").toString(), transaction.get("date").toString());
		assertEquals(transactionJson.get("amount").toString(), transaction.get("amount").toString());
		assertEquals(transactionJson.get("externalIBAN").toString(), transaction.get("externalIBAN"));
		assertEquals(transactionJson.get("type").toString(), transaction.get("type"));
		
		now = LocalDateTime.now().toString();
		transaction.put("date", now)
				.put("amount", 42)
				.put("externalIBAN", "NL39RABO0300065865")
				.put("type", "withdrawal");
		
		// Valid session id as parameter
		Response parameterTransactionResponse = given().
			header("Content-Type", "application/JSON").
			body(transaction.toString()).
		when().
			put("/transactions/" + validTransactionID + "?session_id=" + sessionID);
		
		// Check the response code
		parameterTransactionResponse.
		then().
			assertThat().statusCode(200);
		
		// Check if the transaction was changed correctly
		transactionJson = parameterTransactionResponse.jsonPath();
		assertEquals(transactionJson.getInt("id"), Integer.parseInt(validTransactionID));
		assertEquals(transactionJson.get("date").toString(), transaction.get("date").toString());
		assertTrue(transactionJson.getFloat("amount")== transaction.getDouble("amount"));
		assertEquals(transactionJson.get("externalIBAN").toString(), transaction.get("externalIBAN"));
		assertEquals(transactionJson.get("type").toString(), transaction.get("type"));
		
		// ---- Non-existent ID ----
		given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
			body(transaction.toString()).
		when().
			put("/transactions/" + -1).
		then().
			assertThat().statusCode(404);
		
		// Invalid input
		// amount = 0
		transaction.put("amount", 0);
		given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
			body(transaction.toString()).
		when().
			put("/transactions/" + validTransactionID).
		then().
			assertThat().statusCode(405);
		// amount is negative
		transaction.put("amount", -15);
		given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
			body(transaction.toString()).
		when().
			put("/transactions/" + validTransactionID).
		then().
			assertThat().statusCode(405);
		
		// null externalIBAN
		transaction.
					put("categoryID", validCategoryID).
					remove("externalIBAN");
		given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
			body(transaction.toString()).
		when().
			put("/transactions/" + validTransactionID).
		then().
			assertThat().statusCode(405);
		
		// null date
		transaction.
					put("externalIBAN", "NL39RABO0300065264").
					remove("date");
		given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
			body(transaction.toString()).
		when().
			put("/transactions/" + validTransactionID).
		then().
			assertThat().statusCode(405);
		
		// wrong type of transaction
		transaction.
		put("type", "invalid_type").put("date", now.toString());
		given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
			body(transaction.toString()).
		when().
			put("/transactions/" + validTransactionID).
		then().
			assertThat().statusCode(405);
		
		// no body
		given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
		when().
			put("/transactions/" + validTransactionID).
		then().
			assertThat().statusCode(405);
	}
	
	@Test
	public void testDeleteTransaction() {
		JsonPath transactionJson = 
		given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
		when().
	        get("/transactions?limit=100").
		then().
			contentType(ContentType.JSON).
		extract().
			response().jsonPath();
		// Get the last transaction id
		int listSize = transactionJson.getList("id").size();
		String lastTransactionID = transactionJson.getList("id").get(listSize - 1).toString();
		// ---- Headers ----
		// Mismatching session IDs
		given().
			header("Content-Type", "application/JSON").
			header("X-session-ID", Integer.parseInt(sessionID)-1).
		when().
			delete("/transactions/" + validTransactionID + "?session_id=" + sessionID).
		then().
			assertThat().statusCode(401);
		// No header
		given().
			header("Content-Type", "application/JSON").
		when().
			delete("/transactions/" + lastTransactionID).
		then().
			assertThat().statusCode(401);
		
		// Invalid header
		given().
			header("X-session-ID", -1).
			header("Content-Type", "application/JSON").
		when().
			delete("/transactions/" + lastTransactionID).
		then().
			assertThat().statusCode(401);
		
		// Valid header
		given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
		when().
			delete("/transactions/" + lastTransactionID).
		then().
			assertThat().statusCode(204);
		
		// Check that transaction was indeed deleted
		// Try another delete
		given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
		when().
			delete("/transactions/" + lastTransactionID).
		then().
			assertThat().statusCode(404);
		// Try a get request
		given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
		when().
			get("/transactions/" + lastTransactionID).
		then().
			assertThat().statusCode(404);
		
		// Valid parameter session id
		
		// Post a new transaction ( should have the same id as the one we just deleted )
		JSONObject newTransaction = new JSONObject().
				put("date", "2018-03-31T22:27:09.140").
				put("amount",201.03).
				put("externalIBAN", "NL39RABO0300065264").
				put("type", "deposit");
		given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
			body(newTransaction.toString()).
		when().
			post("/transactions").
		then().
			assertThat().statusCode(201);
		
		// Perform the delete and check the response code
		given().
			header("Content-Type", "application/JSON").
		when().
			delete("/transactions/" + lastTransactionID + "?session_id=" + sessionID).
		then().
			assertThat().statusCode(204);
		
		// Check that transaction was indeed deleted
		// Try another delete
		given().
			header("Content-Type", "application/JSON").
		when().
			delete("/transactions/" + lastTransactionID  + "?session_id=" + sessionID).
		then().
			assertThat().statusCode(404);
		// Try a get request
		given().
			header("Content-Type", "application/JSON").
		when().
			get("/transactions/" + lastTransactionID  + "?session_id=" + sessionID).
		then().
			assertThat().statusCode(404);
		
	}
	
	@Test
	public void testPatchTransaction() {
		// Get the categoryID of a valid transaction
		JsonPath transactionJson = 
		given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
		when().
	        get("/transactions/" + validTransactionID).
		then().
			contentType(ContentType.JSON).
		extract().
			response().jsonPath();
		
		HashMap<String, Integer> categoryJson = transactionJson.get("category");
		JSONObject incrementedCategoryID = new JSONObject();
		if (categoryJson != null) {
			// if the transaction has a category, increment the category nr by one
			// assumes there exists a category after the present category
			incrementedCategoryID.put("category_id", categoryJson.get("id") + 1);
		} else {
			// if the category is null, give the field a valid category
			incrementedCategoryID.put("category_id", Integer.parseInt(validCategoryID));
		}
		
		// ---- Headers ----
		// Mismatching session IDs
		given().
			header("Content-Type", "application/JSON").
			header("X-session-ID", Integer.parseInt(sessionID)-1).
			body(incrementedCategoryID.toString()).
		when().
			patch("/transactions/" + validTransactionID + "/category?session_id=" + sessionID).
		then().
			assertThat().statusCode(401);
		// No header
		given().
			header("Content-Type", "application/JSON").
			body(incrementedCategoryID.toString()).
		when().
			patch("/transactions/" + validTransactionID + "/category").
		then().
			assertThat().statusCode(401);
		
		// Invalid header
		given().
			header("X-session-ID", -1).
			header("Content-Type", "application/JSON").
			body(incrementedCategoryID.toString()).
		when().
			patch("/transactions/" + validTransactionID + "/category").
		then().
			assertThat().statusCode(401);
		
		
		// Valid header
		
		Response response = given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
			body(incrementedCategoryID.toString()).
		when().
			patch("/transactions/" + validTransactionID + "/category");
		
		response.
		then().
			assertThat().statusCode(200);
		
		// Get the transaction, to check if the patch worked
		HashMap<String, Integer> category = response.
		then().
			contentType(ContentType.JSON).
		extract().
			response().jsonPath().get("category");
		int categoryID = category.get("id");

		assertEquals(categoryID, incrementedCategoryID.get("category_id"));
		
		// Perform the patch and check the response code
		JSONObject newCategoryID = new JSONObject().put("category_id", 
				incrementedCategoryID.getInt("category_id") + 1  // Update the category id further
				);
		response = given().
			header("Content-Type", "application/JSON").
			body(newCategoryID.toString()).
		when().
			patch("/transactions/" + validTransactionID + "/category?session_id=" + sessionID);
		// Check response code
		response.
			then().
				assertThat().statusCode(200);
		
		assertEquals(response.jsonPath().getInt("category.id"), newCategoryID.getInt("category_id"));
		
		
		
		
		// ---- Non-existent IDs ----
		// Invalid transactionID
		given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
			body(incrementedCategoryID.toString()).
		when().
			// Id out of the session or possibly out of valid id range
			patch("/transactions/" + (Integer.parseInt(validTransactionID) - 1) + "/category").
		then().
			assertThat().statusCode(404);
		
		// Invalid categoryID
		given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
			// invalid category_id
			body(new JSONObject().put("category_id", -1).toString()).
		when().
			patch("/transactions/" + validTransactionID + "/category").
		then().
			assertThat().statusCode(404);
		
		// Invalid both transactionID and categoryID
		given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
			// invalid category_id
			body(new JSONObject().put("category_id", -1).toString()).
		when().
			// ID that is out of the session or possibly out of valid id range
			patch("/transactions/" + (Integer.parseInt(validTransactionID) - 1) + "/category").
		then().
			assertThat().statusCode(404);
		
		// No body
		given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
		when().
			patch("/transactions/" + validTransactionID + "/category").
		then().
			assertThat().statusCode(405);
		
	}
	
	@Test
	public void testGetCategories() {
		// ---- Headers ----
		// Mismatching session IDs
		given().
			header("Content-Type", "application/JSON").
			header("X-session-ID", Integer.parseInt(sessionID)-1).
		when().
			get("categories?session_id=" + sessionID).
		then().
			assertThat().statusCode(401);
		
		// Valid header
		Response headerCategory = given().
				header("X-session-ID", sessionID).
				header("Content-Type", "application/JSON").
		when().
				get("/categories");
		
		// Checks status code
		headerCategory.
			then().
				assertThat().statusCode(200);
		// Check if IDs make sense
		for (Object id: headerCategory.jsonPath().getList("id")) {
			assertTrue((int)id >= 0);
		}
		
		// Check if the names are not null
		for (Object name: headerCategory.jsonPath().getList("name")) {
			assertTrue(name != null);
		}
		
		// Do the same tests with the sessionID passed as a parameter
		Response parameterCategory = given().
				header("Content-Type", "application/JSON").
		when().
				get("/categories?session_id=" + sessionID);
		
		// Check if the ids of the responses are the same
		assertEquals(parameterCategory.jsonPath().getList("id"), headerCategory.jsonPath().getList("id"));
		
		// Check if the names of the responses are the same
		assertEquals(parameterCategory.jsonPath().getList("name"), headerCategory.jsonPath().getList("name"));
		
		
		// Invalid header
		given().
			header("X-session-ID", -1).
			header("Content-Type", "application/JSON").
		when().
			get("/categories").
		then().
			assertThat().statusCode(401);
		// No header
		given().
			header("Content-Type", "application/JSON").
		when().
			get("/categories").
		then().
			assertThat().statusCode(401);
	}
	
	@Test
	public void testPostCategory() {
		JSONObject category = new JSONObject()
											.put("name", "blah");
		
		// ---- Headers ----
		// Mismatching session IDs
		given().
			header("Content-Type", "application/JSON").
			header("X-session-ID", Integer.parseInt(sessionID)-1).
			body(category.toString()).
		when().
			post("categories?session_id=" + sessionID).
		then().
			assertThat().statusCode(401);
		// No header
		given().
			header("Content-Type", "application/JSON").
			body(category.toString()).
		when().
			post("/categories").
		then().
			assertThat().statusCode(401);
		
		// Invalid header
		given().
			header("X-session-ID", -1).
			header("Content-Type", "application/JSON").
			body(category.toString()).
		when().
			post("/categories").
		then().
			assertThat().statusCode(401);
		
		// Valid header
		Response headerCategoryResponse = given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
			body(category.toString()).
		when().
			post("/categories");
		
		// Check the status code
		headerCategoryResponse.
			then().
				assertThat().statusCode(201);
		// Check the response body
		JsonPath categoryJson = headerCategoryResponse.jsonPath();
		// Check if the ID makes sense
		assertTrue(categoryJson.getInt("id") > 0);
		// Check if the name is correct
		assertEquals(categoryJson.getString("name"), category.get("name"));
		
		// Check if the session ID passed as a parameter works
		category.put("name", "blah blah");

		Response parameterCategoryResponse = given().
			header("Content-Type", "application/JSON").
			body(category.toString()).
		when().
			post("/categories?session_id=" + sessionID);
		
		// Check if the id is properly incremented
		assertEquals(parameterCategoryResponse.jsonPath().getInt("id"), headerCategoryResponse.jsonPath().getInt("id") + 1);
		
		// Check if the name is the same as what was posted
		assertEquals(parameterCategoryResponse.jsonPath().getString("name"),category.getString("name"));
		
		// ---- Invalid input ----
		// name is null
		category.remove("name");
		given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
			body(category.toString()).
		when().
			post("/categories").
		then().
			assertThat().statusCode(405);
		
		// no body
		given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
		when().
			post("/categories").
		then().
			assertThat().statusCode(405);
}
	
	@Test
	public void testGetCategory() {
		// ---- Headers ----
		// Mismatching session IDs
		given().
			header("Content-Type", "application/JSON").
			header("X-session-ID", Integer.parseInt(sessionID)-1).
		when().
			get("categories/" + validCategoryID +"?session_id=" + sessionID).
		then().
			assertThat().statusCode(401);
		// No header
		given().
			header("Content-Type", "application/JSON").
		when().
			get("/categories/" + validCategoryID).
		then().
			assertThat().statusCode(401);
		
		// Invalid header
		given().
			header("X-session-ID", -1).
			header("Content-Type", "application/JSON").
		when().
			get("/categories/" + validCategoryID).
		then().
			assertThat().statusCode(401);
		
		// Valid header
		Response headerCategory = given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
		when().
			get("/categories/" + validCategoryID);
		
		// check response code
		headerCategory.then().
			assertThat().statusCode(200);
		
		// check response body
		JsonPath categoryJson = headerCategory.jsonPath();
		assertEquals(categoryJson.getInt("id"), Integer.parseInt(validCategoryID));
		assertNotNull(categoryJson.getString("name"));
		
		// Check if the GET request with the session ID passed as a parameter yields the same category
		Response parameterCategory = given().
				header("Content-Type", "application/JSON").
			when().
				get("/categories/" + validCategoryID + "?session_id=" + sessionID);
		
		// Check if the id is the same
		assertEquals(parameterCategory.jsonPath().getInt("id"), headerCategory.jsonPath().getInt("id"));
		// Check if the name is the same
		assertEquals(parameterCategory.jsonPath().getString("name"), headerCategory.jsonPath().getString("name"));
		
		// ---- Invalid path ----
		// negative id
		given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
		when().
			get("/categories/" + -1).
		then().
			assertThat().statusCode(404);
		// negative or out of session
		given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
		when().
			// Negative or possibly out of valid index range
			get("/categories/" + (Integer.parseInt(validCategoryID) - 1)).
		then().
			assertThat().statusCode(404);
	}
	
	@Test
	public void testPutCategory() {
		JSONObject category = new JSONObject()
				.put("name", "putHeaderCategoryTest");

		// ---- Headers ----
		// Mismatching session IDs
		given().
			header("Content-Type", "application/JSON").
			header("X-session-ID", Integer.parseInt(sessionID)-1).
			body(category.toString()).
		when().
			put("categories/" + validCategoryID + "?session_id=" + sessionID).
		then().
			assertThat().statusCode(401);
		// No header
		given().
			header("Content-Type", "application/JSON").
			body(category.toString()).
		when().
			put("/categories/" + validCategoryID).
		then().
			assertThat().statusCode(401);
		
		// Invalid header
		given().
			header("X-session-ID", -1).
			header("Content-Type", "application/JSON").
			body(category.toString()).
		when().
			put("/categories/" + validCategoryID).
		then().
			assertThat().statusCode(401);

		// Valid header
		Response headerCategoryResponse = given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
			body(category.toString()).
		when().
			put("/categories/" + validCategoryID);
		
		//SessionID given as parameter
		Response parameterCategoryResponse = given().
			header("Content-Type", "application/JSON").
			body(category.toString()).
		when().
			put("/categories/" + validCategoryID + "?session_id=" + sessionID);
		
		// Check response codes
		headerCategoryResponse.
			then().
				assertThat().statusCode(200);
		
		parameterCategoryResponse.
		then().
			assertThat().statusCode(200);
		
		// Check response body
		JsonPath headerCategoryJson = headerCategoryResponse.jsonPath();
		JsonPath parameterCategoryJson = parameterCategoryResponse.jsonPath();
		
		// Check if the request works for the sessionID given in the header
		assertEquals(headerCategoryJson.getInt("id"), Integer.parseInt(validCategoryID));
		assertEquals(headerCategoryJson.getString("name"), category.get("name"));
		
		
		// Check if the PUT request with the sessionID passed down as a parameter also works
		assertEquals(parameterCategoryJson.getInt("id"), Integer.parseInt(validCategoryID));
		assertEquals(parameterCategoryJson.getString("name"), category.get("name"));
		
		
		
		// ---- Invalid path ----
		// negative id
		given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
			body(category.toString()).
		when().
			put("/categories/" + -1).
		then().
			assertThat().statusCode(404);
		// negative or out of session id
		given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
			body(category.toString()).
		when().
			put("/categories/" + (Integer.parseInt(validCategoryID) - 1)).
		then().
			assertThat().statusCode(404);
		
		// ---- Invalid input ----
		// null name
		category.remove("name");
		given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
			body(category.toString()).
		when().
			put("/categories/" + validCategoryID).
		then().
			assertThat().statusCode(405);
		// no body
		given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
		when().
			put("/categories/" + validCategoryID).
		then().
			assertThat().statusCode(405);
		
	}
	
	@Test
	public void testDeleteCategory() {
		JsonPath categoryJson = 
		given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
		when().
	        get("/categories").
		then().
			contentType(ContentType.JSON).
		extract().
			response().jsonPath();
		// Get the last transaction id
		int listSize = categoryJson.getList("id").size();
		String lastCategoryID = categoryJson.getList("id").get(listSize - 1).toString();
		// ---- Headers ----
		// Mismatching session IDs
		given().
			header("Content-Type", "application/JSON").
			header("X-session-ID", Integer.parseInt(sessionID)-1).
		when().
			delete("categories/" + validCategoryID + "?session_id=" + sessionID).
		then().
			assertThat().statusCode(401);
		// No header
		given().
			header("Content-Type", "application/JSON").
		when().
			delete("/categories/" + lastCategoryID).
		then().
			assertThat().statusCode(401);
		
		// Invalid header
		given().
			header("X-session-ID", -1).
			header("Content-Type", "application/JSON").
		when().
			delete("/categories/" + lastCategoryID).
		then().
			assertThat().statusCode(401);
		
		// Valid header
		given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
		when().
			delete("/categories/" + lastCategoryID).
		then().
			assertThat().statusCode(204);
		
		// Check that category was indeed deleted
		// Try another delete
		given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
		when().
			delete("/categories/" + lastCategoryID).
		then().
			assertThat().statusCode(404);
		// Try a get request
		given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
		when().
			get("/categories/" + lastCategoryID).
		then().
			assertThat().statusCode(404);
		
		// Add the category again to test the delete with the sessionID inserted as a parameter
		given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
			body(new JSONObject().put("name", "parameterTest").toString()).
		when().
			post("/categories").
		then().
			assertThat().statusCode(201);
		
		// Perform the delete
		given().
			header("Content-Type", "application/JSON").
		when().
			delete("/categories/" + lastCategoryID + "?session_id=" + sessionID).
		then().
			assertThat().statusCode(204);
		
		// Check that category was indeed deleted
		// Try another delete
		given().
			header("Content-Type", "application/JSON").
		when().
			delete("/categories/" + lastCategoryID + "?session_id=" + sessionID).
		then().
			assertThat().statusCode(404);
		// Try a get request
		given().
			header("X-session-ID", sessionID).
			header("Content-Type", "application/JSON").
		when().
			get("/categories/" + lastCategoryID).
		then().
			assertThat().statusCode(404);
		
	}
	
	@Test
	public void testPostSession() {
		String session = 
		given().
		        contentType("application/json").
		when().
		        post("/sessions").
		then().
				contentType(ContentType.JSON).
		extract().
				response().jsonPath().getString("id");
		
		assertTrue(Integer.parseInt(session) > 0);
		
	}
}
