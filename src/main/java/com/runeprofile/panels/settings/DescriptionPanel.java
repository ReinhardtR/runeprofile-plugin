package com.runeprofile.panels.settings;

import com.runeprofile.RuneProfileConfig;
import com.runeprofile.RuneProfilePlugin;
import com.runeprofile.utils.DocumentSizeFilter;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import java.awt.*;

public class DescriptionPanel extends JPanel {
	private static final int maxLength = 100;
	private final JLabel descriptionTitle = new JLabel("Description (" + "0" + "/" + maxLength + ")");
	private final JTextArea descriptionEditor = new JTextArea(9, 0);

	public DescriptionPanel(RuneProfilePlugin runeProfilePlugin) {
		setLayout(new DynamicGridLayout(0, 1, 0, 4));

		descriptionTitle.setFont(FontManager.getRunescapeBoldFont());
		descriptionTitle.setForeground(Color.WHITE);

		JPanel container = new JPanel();
		container.setLayout(new BorderLayout());
		container.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		container.setBorder(new EmptyBorder(8, 8, 8, 8));

		descriptionEditor.setTabSize(2);
		descriptionEditor.setSize(new Dimension(46 * 4 + 3, 200));
		descriptionEditor.setLineWrap(true);
		descriptionEditor.setWrapStyleWord(true);
		descriptionEditor.setOpaque(false);

		initText();
		initMaxLengthFilter();
		initDocumentListener();

		container.add(descriptionEditor);

		JButton updateButton = new JButton("Update Description");
		updateButton.addActionListener((event) -> SwingUtilities.invokeLater(() -> {
			updateButton.setEnabled(false);

			try {
				String newDescription = runeProfilePlugin.updateDescription(descriptionEditor.getText());

				RuneProfilePlugin.getConfigManager().setRSProfileConfiguration(
								RuneProfileConfig.CONFIG_GROUP,
								RuneProfileConfig.DESCRIPTION,
								newDescription
				);

				// Sync the description with the server
				descriptionEditor.setText(newDescription);
			} catch (Exception e) {
				e.printStackTrace();
			}

			updateButton.setEnabled(true);
		}));

		add(descriptionTitle);
		add(container);
		add(updateButton);
	}

	private void initText() {
		String storedDescription = RuneProfilePlugin.getConfigManager().getRSProfileConfiguration(
						RuneProfileConfig.CONFIG_GROUP,
						RuneProfileConfig.DESCRIPTION
		);

		descriptionEditor.setText(storedDescription);
		updateDescriptionCount();
	}

	private void updateDescriptionCount() {
		descriptionTitle.setText("Description (" + descriptionEditor.getDocument().getLength() + "/" + maxLength + ")");
	}

	private void initMaxLengthFilter() {
		AbstractDocument document = (AbstractDocument) descriptionEditor.getDocument();
		document.setDocumentFilter(new DocumentSizeFilter(maxLength));
	}

	private void initDocumentListener() {
		descriptionEditor.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				updateDescriptionCount();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				updateDescriptionCount();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				updateDescriptionCount();
			}
		});
	}
}
