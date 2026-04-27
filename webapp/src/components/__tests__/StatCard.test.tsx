import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { StatCard } from "@/components/StatCard";

describe("StatCard", () => {
  it("renders the label", () => {
    render(<StatCard label="Transactions" value={42} />);
    expect(screen.getByText("Transactions")).toBeInTheDocument();
  });

  it("renders a numeric value formatted in French locale", () => {
    render(<StatCard label="Price" value={1500} />);
    expect(screen.getByText(/1[\s\u202f]500/)).toBeInTheDocument();
  });

  it("renders a string value as-is", () => {
    render(<StatCard label="Range" value="100k - 500k" />);
    expect(screen.getByText("100k - 500k")).toBeInTheDocument();
  });

  it("renders the unit when provided", () => {
    render(<StatCard label="Average" value={250000} unit="€" />);
    expect(screen.getByText("€")).toBeInTheDocument();
  });

  it("does not render a unit when not provided", () => {
    const { container } = render(<StatCard label="Count" value={10} />);
    const spans = container.querySelectorAll("span");
    const unitSpans = Array.from(spans).filter((s) =>
      s.classList.contains("text-muted-foreground"),
    );
    expect(unitSpans).toHaveLength(0);
  });
});
