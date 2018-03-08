package client;

import java.util.Scanner;

public class ClientMain {

	public static void main(String[] args) throws Exception {
		System.out.println("Welcome to the fake client testing center");
		System.out.println("\nChoose something to test: ");

		System.out.println("0: Normal get request that should be successful");
		System.out.println("1: No empty byte between file name and mode(octet)");
		System.out.println("2: Wrong OP code");
		System.out.println("3: Test wait 5 seconds before sending ACK");
		System.out.println("5: Send wrong block number once");

		System.out.print("\nYour choice: ");
		Scanner in = new Scanner(System.in);
		int input = in.nextInt();

			switch(input) {
				case 0: TestNormal.runTest();
				break;
				
				case 1: TestMissingEmptyByte.runTest();
				break;
				
				case 2: TestWrongOP.runTest();
				break;
				
				case 3: TestWait.runTest();
				break;
				
				case 5: TestWrongBlockNum.runTest();
				break;
			}
	}
}
