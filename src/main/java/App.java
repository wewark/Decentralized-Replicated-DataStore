import forms.LoginForm;
import storage.FileManager;

import javax.swing.*;

public class App {
	public static void main(String[] args) {
		System.out.println("DEVELOPMENT: Enter working Directory (e.g \"Data**\" ");
		FileManager.setRootDir();
		JFrame frame = new LoginForm();
		frame.pack();
		frame.setResizable(false);
		frame.setVisible(true);
		frame.setLocationRelativeTo(null);
	}
}
