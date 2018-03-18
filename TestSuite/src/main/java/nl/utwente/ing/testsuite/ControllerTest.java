package nl.utwente.ing.testsuite;
import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.List;

import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Test;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;

public class ControllerTest {
	private static String sessionID;
	private static String validCategoryID;
	private static String validTransactionID;
	
	@BeforeClass
	public static void before() {
		RestAssured.basePath = "/api/v1";
		sessionID = 
		given().
		        contentType("application/json").
		when().
		        get("/sessions").
		then().
				contentType(ContentType.JSON).
		extract().
				response().asString();
		
		// Add some categories to test with
		JSONObject category = new JSONObject()
											.put("name", "test1");
		given().
			header("WWW_Authenticate", sessionID).
			header("Content-Type", "application/JSON").
			body(category.toString()).
		when().
			post("/categories");
		
		category.put("name", "test2");
		given().
			header("WWW_Authenticate", sessionID).
			header("Content-Type", "application/JSON").
			body(category.toString()).
		when().
			post("/categories");
		// Get a valid categoryID to work with
		JsonPath categoryJson = 
				given().
						header("WWW_Authenticate", sessionID).
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
		JSONObject transaction = new JSONObject().put("date", "0")
												.put("amount", 10.0)
												.put("externalIBAN", "testIBAN")
												.put("type", "deposit")
												.put("categoryID", validCategoryID);
		given().
			header("WWW_Authenticate", sessionID).
			header("Content-Type", "application/JSON").
			body(transaction.toString()).
		when().
			post("/transactions");
		transaction.put("date", 1);
		given().
			header("WWW_Authenticate", sessionID).
			header("Content-Type", "application/JSON").
			body(transaction.toString()).
		when().
			post("/transactions");
		
		// Get a valid transaction id to work with
		JsonPath transactionJson = 
			given().
				header("WWW_Authenticate", sessionID).
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
		// Valid header
		given().
				header("WWW_Authenticate", sessionID).
		when().
				get("/transactions").
		then().
				assertThat().statusCode(200);
		// Invalid header
		given().
			header("WWW_Authenticate", -1).
		when().
			get("/transactions").
		then().
			assertThat().statusCode(401);
		// No header
		when().
			get("/transactions").
		then().
			assertThat().statusCode(401);
		// ---- Offset ----
		
		// Get the first transaction id starting with offset 0
		JsonPath transactionJson = 
			given().
				header("WWW_Authenticate", sessionID).
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
				header("WWW_Authenticate", sessionID).
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
		
		// ---- Limit ----
		
		transactionJson = 
			given().
				header("WWW_Authenticate", sessionID).
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
		
		// ---- Category ----
		transactionJson = 
			given().
				header("WWW_Authenticate", sessionID).
				header("Content-Type", "application/JSON").
				param("category", validCategoryID).
			when().
				get("/transactions").
			then().
				contentType(ContentType.JSON).
			extract().
				response().jsonPath();
		List<Integer> categoryIds = transactionJson.getList("categoryID");
		// Test all returned category Ids
		for (int categoryID : categoryIds) {
			assertEquals(categoryID, Integer.parseInt(validCategoryID));
		}
	}
	
	@Test
	public void testPostTransaction() {
		JSONObject transaction = new JSONObject().put("date", "0")
												.put("amount", 15.0)
												.put("externalIBAN", "testIBAN")
												.put("type", "deposit")
												.put("categoryID", validCategoryID);
		
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
			header("WWW_Authenticate", -1).
			header("Content-Type", "application/JSON").
			body(transaction.toString()).
		when().
			post("/transactions").
		then().
			assertThat().statusCode(401);
		
		// Valid header
		given().
			header("WWW_Authenticate", sessionID).
			header("Content-Type", "application/JSON").
			body(transaction.toString()).
		when().
			post("/transactions").
		then().
			assertThat().statusCode(201);
		
		// Invalid input
		// amount = 0
		transaction.put("amount", 0);
		given().
			header("WWW_Authenticate", sessionID).
			header("Content-Type", "application/JSON").
			body(transaction.toString()).
		when().
			post("/transactions").
		then().
			assertThat().statusCode(405);
		// amount is negative
		transaction.put("amount", -15);
		given().
			header("WWW_Authenticate", sessionID).
			header("Content-Type", "application/JSON").
			body(transaction.toString()).
		when().
			post("/transactions").
		then().
			assertThat().statusCode(405);
		
		// non-existent categoryID
		transaction.put("amount", 15.0)
					.put("categoryID", -1);
		given().
			header("WWW_Authenticate", sessionID).
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
			header("WWW_Authenticate", sessionID).
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
			header("WWW_Authenticate", sessionID).
			header("Content-Type", "application/JSON").
			body(transaction.toString()).
		when().
			post("/transactions").
		then().
			assertThat().statusCode(405);
	}
	
	@Test
	public void testGetTransaction() {
		// ---- Headers ----
		// No header
		given().
			header("Content-Type", "application/JSON").
		when().
			get("/transactions/" + validTransactionID).
		then().
			assertThat().statusCode(401);
		
		// Invalid header
		given().
			header("WWW_Authenticate", -1).
			header("Content-Type", "application/JSON").
		when().
			get("/transactions/" + validTransactionID).
		then().
			assertThat().statusCode(401);
		
		// Valid header
		given().
			header("WWW_Authenticate", sessionID).
			header("Content-Type", "application/JSON").
		when().
			get("/transactions/" + validTransactionID).
		then().
			assertThat().statusCode(200);
		
		// ---- Non-existent ID ----
		given().
			header("WWW_Authenticate", sessionID).
			header("Content-Type", "application/JSON").
		when().
			get("/transactions/" + -1).
		then().
			assertThat().statusCode(404);
	}
	
	@Test
	public void testPutTransaction() {
		JSONObject transaction = new JSONObject().put("date", "timeOfPutRequest")
				.put("amount", 999.0)
				.put("externalIBAN", "putIBAN")
				.put("type", "deposit")
				.put("categoryID", validCategoryID);
		// ---- Headers ----
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
			header("WWW_Authenticate", -1).
			header("Content-Type", "application/JSON").
			body(transaction.toString()).
		when().
			put("/transactions/" + validTransactionID).
		then().
			assertThat().statusCode(401);
		
		// Valid header
		given().
			header("WWW_Authenticate", sessionID).
			header("Content-Type", "application/JSON").
			body(transaction.toString()).
		when().
			put("/transactions/" + validTransactionID).
		then().
			assertThat().statusCode(200);
		
		// Check if transaction has changed
		JsonPath transactionJson = 
			given().
				header("WWW_Authenticate", sessionID).
				header("Content-Type", "application/JSON").
			when().
		        get("/transactions/" + validTransactionID).
			then().
				contentType(ContentType.JSON).
			extract().
				response().jsonPath();
		
		// Check if each parameter is the same as in the put request
		assertEquals(transactionJson.get("date").toString(), transaction.get("date"));
		assertEquals(transactionJson.get("amount").toString(), transaction.get("amount").toString());
		assertEquals(transactionJson.get("externalIBAN").toString(), transaction.get("externalIBAN"));
		assertEquals(transactionJson.get("type").toString(), transaction.get("type"));
		assertEquals(transactionJson.get("categoryID").toString(), transaction.get("categoryID"));
		
		// ---- Non-existent ID ----
		given().
			header("WWW_Authenticate", sessionID).
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
			header("WWW_Authenticate", sessionID).
			header("Content-Type", "application/JSON").
			body(transaction.toString()).
		when().
			put("/transactions/" + validTransactionID).
		then().
			assertThat().statusCode(405);
		// amount is negative
		transaction.put("amount", -15);
		given().
			header("WWW_Authenticate", sessionID).
			header("Content-Type", "application/JSON").
			body(transaction.toString()).
		when().
			put("/transactions/" + validTransactionID).
		then().
			assertThat().statusCode(405);
		
		// non-existent categoryID
		transaction.put("amount", 15.0)
					.put("categoryID", -1);
		given().
			header("WWW_Authenticate", sessionID).
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
			header("WWW_Authenticate", sessionID).
			header("Content-Type", "application/JSON").
			body(transaction.toString()).
		when().
			put("/transactions/" + validTransactionID).
		then().
			assertThat().statusCode(405);
		
		// null date
		transaction.
					put("externalIBAN", "testIBAN").
					remove("date");
		given().
			header("WWW_Authenticate", sessionID).
			header("Content-Type", "application/JSON").
			body(transaction.toString()).
		when().
			put("/transactions/" + validTransactionID).
		then().
			assertThat().statusCode(405);

	}
	
	@Test
	public void testDeleteTransaction() {
		JsonPath transactionJson = 
		given().
			header("WWW_Authenticate", sessionID).
			header("Content-Type", "application/JSON").
		when().
	        get("/transactions").
		then().
			contentType(ContentType.JSON).
		extract().
			response().jsonPath();
		// Get the last transaction id
		int listSize = transactionJson.getList("id").size();
		String lastTransactionID = transactionJson.getList("id").get(listSize - 1).toString();
		// ---- Headers ----
		// No header
		given().
			header("Content-Type", "application/JSON").
		when().
			delete("/transactions/" + lastTransactionID).
		then().
			assertThat().statusCode(401);
		
		// Invalid header
		given().
			header("WWW_Authenticate", -1).
			header("Content-Type", "application/JSON").
		when().
			delete("/transactions/" + lastTransactionID).
		then().
			assertThat().statusCode(401);
		
		// Valid header
		given().
			header("WWW_Authenticate", sessionID).
			header("Content-Type", "application/JSON").
		when().
			delete("/transactions/" + lastTransactionID).
		then().
			assertThat().statusCode(204);
		
		// Check that transaction was indeed deleted
		// Try another delete
		given().
			header("WWW_Authenticate", sessionID).
			header("Content-Type", "application/JSON").
		when().
			delete("/transactions/" + lastTransactionID).
		then().
			assertThat().statusCode(404);
		// Try a get request
		given().
			header("WWW_Authenticate", sessionID).
			header("Content-Type", "application/JSON").
		when().
			get("/transactions/" + lastTransactionID).
		then().
			assertThat().statusCode(404);
	}
	
	@Test
	public void testPatchTransaction() {
		// Get the categoryID of a valid transaction
		JsonPath transactionJson = 
		given().
			header("WWW_Authenticate", sessionID).
			header("Content-Type", "application/JSON").
		when().
	        get("/transactions/" + validTransactionID).
		then().
			contentType(ContentType.JSON).
		extract().
			response().jsonPath();
		int incrementedCategoryID = transactionJson.getInt("categoryID") + 1;
		
		// ---- Headers ----
		// No header
		given().
			header("Content-Type", "application/JSON").
			body(incrementedCategoryID).
		when().
			patch("/transactions/" + validTransactionID + "/category").
		then().
			assertThat().statusCode(401);
		
		// Invalid header
		given().
			header("WWW_Authenticate", -1).
			header("Content-Type", "application/JSON").
			body(incrementedCategoryID).
		when().
			patch("/transactions/" + validTransactionID + "/category").
		then().
			assertThat().statusCode(401);
		
		// Valid header
		given().
			header("WWW_Authenticate", sessionID).
			header("Content-Type", "application/JSON").
			body(incrementedCategoryID).
		when().
			patch("/transactions/" + validTransactionID + "/category").
		then().
			assertThat().statusCode(200);
		
		// Get the transaction again, to check if the patch worked
		transactionJson = 
		given().
			header("WWW_Authenticate", sessionID).
			header("Content-Type", "application/JSON").
		when().
	        get("/transactions/" + validTransactionID).
		then().
			contentType(ContentType.JSON).
		extract().
			response().jsonPath();
		int categoryID = transactionJson.getInt("categoryID");
		
		assertEquals(categoryID, incrementedCategoryID);
		
		// ---- Non-existent IDs ----
		// Invalid transactionID
		given().
			header("WWW_Authenticate", sessionID).
			header("Content-Type", "application/JSON").
			body(incrementedCategoryID).
		when().
			// Id out of the session or possibly out of valid id range
			patch("/transactions/" + (Integer.parseInt(validTransactionID) - 1) + "/category").
		then().
			assertThat().statusCode(404);
		
		// Invalid categoryID
		given().
			header("WWW_Authenticate", sessionID).
			header("Content-Type", "application/JSON").
			body(-1).
		when().
			// Id out of the session or possibly out of valid id range
			patch("/transactions/" + validTransactionID + "/category").
		then().
			assertThat().statusCode(404);
		
		// Invalid both transactionID and categoryID
		given().
			header("WWW_Authenticate", sessionID).
			header("Content-Type", "application/JSON").
			body(-1).
		when().
			// Id out of the session or possibly out of valid id range
			patch("/transactions/" + (Integer.parseInt(validTransactionID) - 1) + "/category").
		then().
			assertThat().statusCode(404);
		
	}
	
	@Test
	public void testGetCategories() {
		// ---- Headers ----
		// Valid header
		given().
				header("WWW_Authenticate", sessionID).
		when().
				get("/categories").
		then().
				assertThat().statusCode(200);
		// Invalid header
		given().
			header("WWW_Authenticate", -1).
		when().
			get("/categories").
		then().
			assertThat().statusCode(401);
		// No header
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
			header("WWW_Authenticate", -1).
			header("Content-Type", "application/JSON").
			body(category.toString()).
		when().
			post("/categories").
		then().
			assertThat().statusCode(401);
		
		// Valid header
		given().
			header("WWW_Authenticate", sessionID).
			header("Content-Type", "application/JSON").
			body(category.toString()).
		when().
			post("/categories").
		then().
			assertThat().statusCode(201);
		
		// ---- Invalid input ----
		// name is null
		category.remove("name");
		given().
			header("WWW_Authenticate", sessionID).
			header("Content-Type", "application/JSON").
			body(category.toString()).
		when().
			post("/categories").
		then().
			assertThat().statusCode(405);
}
	
	@Test
	public void testGetCategory() {
		// ---- Headers ----
		// No header
		given().
			header("Content-Type", "application/JSON").
		when().
			get("/categories/" + validCategoryID).
		then().
			assertThat().statusCode(401);
		
		// Invalid header
		given().
			header("WWW_Authenticate", -1).
			header("Content-Type", "application/JSON").
		when().
			get("/categories/" + validCategoryID).
		then().
			assertThat().statusCode(401);
		
		// Valid header
		given().
			header("WWW_Authenticate", sessionID).
			header("Content-Type", "application/JSON").
		when().
			get("/categories/" + validCategoryID).
		then().
			assertThat().statusCode(200);
		// ---- Invalid path ----
		// negative id
		given().
			header("WWW_Authenticate", sessionID).
			header("Content-Type", "application/JSON").
		when().
			get("/categories/" + -1).
		then().
			assertThat().statusCode(404);
		// negative or out of session
		given().
			header("WWW_Authenticate", sessionID).
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
				.put("name", "putCategoryTest");

		// ---- Headers ----
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
			header("WWW_Authenticate", -1).
			header("Content-Type", "application/JSON").
			body(category.toString()).
		when().
			put("/categories/" + validCategoryID).
		then().
			assertThat().statusCode(401);
		
		// Valid header
		given().
			header("WWW_Authenticate", sessionID).
			header("Content-Type", "application/JSON").
			body(category.toString()).
		when().
			put("/categories/" + validCategoryID).
		then().
			assertThat().statusCode(200);
		
		// ---- Invalid path ----
		// negative id
		given().
			header("WWW_Authenticate", sessionID).
			header("Content-Type", "application/JSON").
			body(category.toString()).
		when().
			put("/categories/" + -1).
		then().
			assertThat().statusCode(404);
		// negative or out of session id
		given().
			header("WWW_Authenticate", sessionID).
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
			header("WWW_Authenticate", sessionID).
			header("Content-Type", "application/JSON").
			body(category.toString()).
		when().
			put("/categories/" + validCategoryID).
		then().
			assertThat().statusCode(405);
		
	}
	
	@Test
	public void testDeleteCategory() {
		JsonPath categoryJson = 
		given().
			header("WWW_Authenticate", sessionID).
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
		// No header
		given().
			header("Content-Type", "application/JSON").
		when().
			delete("/categories/" + lastCategoryID).
		then().
			assertThat().statusCode(401);
		
		// Invalid header
		given().
			header("WWW_Authenticate", -1).
			header("Content-Type", "application/JSON").
		when().
			delete("/categories/" + lastCategoryID).
		then().
			assertThat().statusCode(401);
		
		// Valid header
		given().
			header("WWW_Authenticate", sessionID).
			header("Content-Type", "application/JSON").
		when().
			delete("/categories/" + lastCategoryID).
		then().
			assertThat().statusCode(204);
		
		// Check that category was indeed deleted
		// Try another delete
		given().
			header("WWW_Authenticate", sessionID).
			header("Content-Type", "application/JSON").
		when().
			delete("/categories/" + lastCategoryID).
		then().
			assertThat().statusCode(404);
		// Try a get request
		given().
			header("WWW_Authenticate", sessionID).
			header("Content-Type", "application/JSON").
		when().
			get("/categories/" + lastCategoryID).
		then().
			assertThat().statusCode(404);
	}
	
	@Test
	public void testGetSession() {
		String session = 
		given().
		        contentType("application/json").
		when().
		        get("/sessions").
		then().
				contentType(ContentType.JSON).
		extract().
				response().asString();
		
		assertTrue(Integer.parseInt(session) > 0);
		
	}
}
