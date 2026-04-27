import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { ExplorerPage } from "@/pages/ExplorerPage";

vi.mock("@/api/hooks", () => ({
  useRegions: () => ({
    data: [
      { code: "11", name: "Île-de-France", population: 12000000, area: 12012 },
      { code: "84", name: "Auvergne-Rhône-Alpes", population: 8000000, area: 69711 },
    ],
  }),
  useDepartments: () => ({
    data: {
      _embedded: {
        departments: [
          { code: "75", name: "Paris", regionCode: "11", population: 2100000, area: 105 },
        ],
      },
      page: { size: 20, totalElements: 1, totalPages: 1, number: 0 },
    },
  }),
  useTransactions: () => ({
    data: {
      _embedded: {
        transactions: [
          {
            id: 1,
            mutationDate: "2024-01-15",
            propertyValue: 350000,
            propertyType: "APARTMENT",
            cityName: "Paris",
            cityInseeCode: "75101",
            builtSurface: 65,
            roomCount: 3,
            landSurface: 0,
          },
          {
            id: 2,
            mutationDate: "2024-02-20",
            propertyValue: 200000,
            propertyType: "HOUSE",
            cityName: "Lyon",
            cityInseeCode: "69123",
            builtSurface: 120,
            roomCount: 5,
            landSurface: 400,
          },
        ],
      },
      page: { size: 20, totalElements: 2, totalPages: 1, number: 0 },
    },
    isLoading: false,
  }),
  useTransactionStats: () => ({
    data: {
      totalTransactions: 2,
      averagePrice: 275000,
      medianPrice: 275000,
      minPrice: 200000,
      maxPrice: 350000,
      averagePricePerSqm: 4230,
    },
  }),
}));

vi.mock("@/components/PriceChart", () => ({
  PriceChart: () => <div data-testid="price-chart" />,
}));

vi.mock("@/components/TransactionMap", () => ({
  TransactionMap: () => <div data-testid="transaction-map" />,
}));

vi.mock("@/components/TransactionDetailDialog", () => ({
  TransactionDetailDialog: () => null,
}));

describe("ExplorerPage", () => {
  it("renders the page title", () => {
    render(<ExplorerPage />);
    expect(screen.getByText("Data Explorer")).toBeInTheDocument();
  });

  it("renders filter labels", () => {
    render(<ExplorerPage />);
    expect(screen.getByText("Region")).toBeInTheDocument();
    expect(screen.getByText("Department")).toBeInTheDocument();
    expect(screen.getByText("Year")).toBeInTheDocument();
    expect(screen.getByText("Type")).toBeInTheDocument();
  });

  it("renders stat cards with transaction stats", () => {
    render(<ExplorerPage />);
    expect(screen.getByText("Transactions")).toBeInTheDocument();
    expect(screen.getByText("Avg. Price")).toBeInTheDocument();
    expect(screen.getByText("Median")).toBeInTheDocument();
  });

  it("renders transaction cards", () => {
    render(<ExplorerPage />);
    expect(screen.getByText("Paris")).toBeInTheDocument();
    expect(screen.getByText("Lyon")).toBeInTheDocument();
    expect(screen.getByText("APARTMENT")).toBeInTheDocument();
    expect(screen.getByText("HOUSE")).toBeInTheDocument();
  });

  it("renders the transactions heading with total count", () => {
    render(<ExplorerPage />);
    const heading = screen.getByRole("heading", { level: 2 });
    expect(heading).toHaveTextContent(/Transactions/);
    expect(heading).toHaveTextContent("(2)");
  });
});
