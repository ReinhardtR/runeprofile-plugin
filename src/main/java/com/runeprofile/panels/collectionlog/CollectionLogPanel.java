package com.runeprofile.panels.collectionlog;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

@Slf4j
public class CollectionLogPanel extends JPanel {
	private final JList<String> missingEntriesList;
	private final DefaultListModel<String> missingEntriesListModel = new DefaultListModel<>();

	public CollectionLogPanel() {
		setLayout(new BorderLayout());

		JTextArea infoText1 = createInfoText("To register your Collection Log, you need to open each entry.");
		JTextArea infoText2 = createInfoText("This list will show all missing entries for the currently selected tab.");

		JPanel infoPanel = new JPanel();
		infoPanel.setLayout(new GridLayout(2, 1, 0, 10));
		infoPanel.setBorder(new EmptyBorder(0, 0, 10, 0));
		infoPanel.add(infoText1);
		infoPanel.add(infoText2);

		add(infoPanel, BorderLayout.NORTH);

		missingEntriesList = new JList<>(missingEntriesListModel);
		missingEntriesList.setLayout(new BorderLayout());
		missingEntriesList.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JScrollPane scrollPane = new JScrollPane(missingEntriesList);

		add(scrollPane, BorderLayout.CENTER);

		loadNoMissingEntriesState();
	}

	private void loadNoMissingEntriesState() {
		SwingUtilities.invokeLater(() -> {
			missingEntriesListModel.removeAllElements();
			missingEntriesListModel.addElement("No missing entries");
			missingEntriesList.revalidate();
			missingEntriesList.repaint();
		});
	}

	public void newTabSelected(List<String> missingEntries) {
		log.info("New tab selected, missing entries: {}", missingEntries);
		SwingUtilities.invokeLater(() -> {
			if (missingEntries.size() == 0) {
				loadNoMissingEntriesState();
			} else {
				missingEntriesListModel.removeAllElements();

				for (String missingEntry : missingEntries) {
					missingEntriesListModel.addElement(missingEntry);
				}

				missingEntriesList.revalidate();
				missingEntriesList.repaint();
			}
		});
	}

	public void newEntrySelected(String entry) {
		log.info("New entry selected: {}", entry);
		SwingUtilities.invokeLater(() -> {
			missingEntriesListModel.removeElement(entry);
			missingEntriesList.revalidate();
			missingEntriesList.repaint();
		});
	}

	private JTextArea createInfoText(String text) {
		JTextArea infoText = new JTextArea(text);
		infoText.setLineWrap(true);
		infoText.setWrapStyleWord(true);
		infoText.setEditable(false);
		infoText.setFocusable(false);
		return infoText;
	}
}
