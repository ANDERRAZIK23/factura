import { Node, mergeAttributes } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import {
  TextStyle,
  FontFamily,
  FontSize,
  Color,
} from "@tiptap/extension-text-style";
import TextAlign from "@tiptap/extension-text-align";
import Highlight from "@tiptap/extension-highlight";
import { TableKit } from "@tiptap/extension-table";

export const contractFields: Record<string, string> = {
  companyName: "Nom de l’entreprise",
  companyAddress: "Adresse de l’entreprise",
  companyIce: "ICE de l’entreprise",
  companyPhone: "Téléphone de l’entreprise",
  companyEmail: "E-mail de l’entreprise",
  clientName: "Nom du client",
  clientAddress: "Adresse du client",
  clientIce: "ICE du client",
  contractReference: "Référence du contrat",
  startDate: "Date de début",
  endDate: "Date de fin",
};

const TemplateField = Node.create({
  name: "templateField",
  group: "inline",
  inline: true,
  atom: true,
  addAttributes() {
    return {
      key: {
        default: "companyName",
        parseHTML: (element) => element.getAttribute("data-contract-field"),
      },
    };
  },
  parseHTML() {
    return [{ tag: "span[data-contract-field]" }];
  },
  renderHTML({ node, HTMLAttributes }) {
    return [
      "span",
      mergeAttributes(HTMLAttributes, {
        "data-contract-field": node.attrs.key,
        class: "contract-template-field",
        contenteditable: "false",
      }),
      contractFields[node.attrs.key] ?? "Champ inconnu",
    ];
  },
  renderText({ node }) {
    return `« ${contractFields[node.attrs.key] ?? "Champ inconnu"} »`;
  },
});

const PageBreak = Node.create({
  name: "pageBreak",
  group: "block",
  atom: true,
  selectable: true,
  parseHTML() {
    return [{ tag: "div[data-contract-page-break]" }];
  },
  renderHTML() {
    return [
      "div",
      {
        "data-contract-page-break": "",
        class: "contract-page-break",
        contenteditable: "false",
      },
      "Saut de page",
    ];
  },
});

export const contractExtensions = [
  StarterKit.configure({
    heading: { levels: [1, 2, 3] },
    code: false,
    codeBlock: false,
    blockquote: false,
    link: false,
  }),
  TextStyle,
  FontFamily,
  FontSize,
  Color,
  TextAlign.configure({ types: ["heading", "paragraph"] }),
  Highlight.configure({ multicolor: true }),
  TableKit.configure({ table: { resizable: true, cellMinWidth: 25 } }),
  TemplateField,
  PageBreak,
];
