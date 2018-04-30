import javax.swing.*;

public class App {
	public static void main(String[] args) {
		JFrame frame = new JFrame("App");
		frame.setContentPane(new LoginForm().panel1);
		frame.pack();
		frame.setResizable(false);
		frame.setVisible(true);
	}
}
