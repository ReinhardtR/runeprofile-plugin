package com.runeprofile.panels.settings;

import com.runeprofile.RuneProfileConfig;
import com.runeprofile.RuneProfilePlugin;
import com.runeprofile.utils.DocumentSizeFilter;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

public class DescriptionPanel extends JPanel {
	private static final int maxLength = 100;
	private final JLabel descriptionTitle = new JLabel("Description (" + "0" + "/" + maxLength + ")");
	private final JTextArea descriptionEditor = new JTextArea(9, 0);

	public DescriptionPanel() {
		super(false);

		this.setLayout(new BorderLayout());

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
		initFocusListener();
		initDocumentListener();

		container.add(descriptionEditor);

		add(descriptionTitle, BorderLayout.PAGE_START);
		add(container);
	}

	private void initText() {
		String storedDescription = RuneProfilePlugin.getConfigManager().getRSProfileConfiguration(
						RuneProfileConfig.CONFIG_GROUP,
						RuneProfileConfig.DESCRIPTION
		);

		descriptionEditor.setText(storedDescription);
		updateDescriptionCount();
	}

	private void initFocusListener() {
		descriptionEditor.addFocusListener(new FocusListener() {
			@Override
			public void focusGained(FocusEvent e) {

			}

			@Override
			public void focusLost(FocusEvent e) {
				String newDescription = descriptionEditor.getText();
				RuneProfilePlugin.getConfigManager().setRSProfileConfiguration(
								RuneProfileConfig.CONFIG_GROUP,
								RuneProfileConfig.DESCRIPTION,
								newDescription
				);
			}
		});
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
