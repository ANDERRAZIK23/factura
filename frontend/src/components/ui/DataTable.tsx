import {
  ColumnDef,
  flexRender,
  getCoreRowModel,
  getSortedRowModel,
  getPaginationRowModel,
  SortingState,
  useReactTable,
  VisibilityState,
  RowSelectionState,
  OnChangeFn,
} from "@tanstack/react-table";
import { useState } from "react";
import { Icon } from "./Icon";
import { Skeleton } from "./Skeleton";
import { ListPagination } from "./ListPagination";

interface DataTableProps<TData, TValue> {
  columns: ColumnDef<TData, TValue>[];
  data: TData[];
  getRowId: (row: TData) => string;
  caption: string;
  itemLabel?: string;
  isLoading?: boolean;
  emptyState?: React.ReactNode;
  rowSelection?: RowSelectionState;
  onRowSelectionChange?: OnChangeFn<RowSelectionState>;
  toolbarActions?: React.ReactNode;
}

export function DataTable<TData, TValue>({
  columns,
  data,
  getRowId,
  caption,
  itemLabel = "résultats",
  isLoading,
  emptyState,
  rowSelection = {},
  onRowSelectionChange,
  toolbarActions,
}: DataTableProps<TData, TValue>) {
  const [sorting, setSorting] = useState<SortingState>([]);
  const [columnVisibility, setColumnVisibility] = useState<VisibilityState>({});

  // If no external state is provided for rowSelection, we keep it internal
  const [internalRowSelection, setInternalRowSelection] =
    useState<RowSelectionState>({});

  const table = useReactTable({
    data,
    columns,
    getRowId,
    getCoreRowModel: getCoreRowModel(),
    onSortingChange: setSorting,
    getSortedRowModel: getSortedRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    initialState: { pagination: { pageIndex: 0, pageSize: 20 } },
    onColumnVisibilityChange: setColumnVisibility,
    onRowSelectionChange: onRowSelectionChange ?? setInternalRowSelection,
    state: {
      sorting,
      columnVisibility,
      rowSelection: onRowSelectionChange ? rowSelection : internalRowSelection,
    },
  });

  return (
    <div className="data-table-container">
      <div className="data-table-toolbar">
        <div className="data-table-actions">{toolbarActions}</div>
        <div className="data-table-visibility">
          <details className="action-menu">
            <summary
              aria-label="Affichage des colonnes"
              title="Affichage des colonnes"
            >
              <Icon name="columns" size={16} />
              <span>Affichage</span>
            </summary>
            <div className="action-menu-panel">
              <div className="action-menu-group">
                {table
                  .getAllColumns()
                  .filter(
                    (column) =>
                      typeof column.accessorFn !== "undefined" &&
                      column.getCanHide(),
                  )
                  .map((column) => {
                    return (
                      <label
                        key={column.id}
                        className="action-menu-item"
                        style={{
                          display: "flex",
                          gap: "0.5rem",
                          alignItems: "center",
                          cursor: "pointer",
                        }}
                      >
                        <input
                          type="checkbox"
                          checked={column.getIsVisible()}
                          disabled={
                            column.getIsVisible() &&
                            table
                              .getVisibleLeafColumns()
                              .filter((item) => item.accessorFn).length === 1
                          }
                          onChange={column.getToggleVisibilityHandler()}
                        />
                        <span>
                          {typeof column.columnDef.header === "string"
                            ? column.columnDef.header
                            : column.id}
                        </span>
                      </label>
                    );
                  })}
              </div>
            </div>
          </details>
        </div>
      </div>

      <div className="table-wrap company-table">
        <table>
          <caption className="sr-only">{caption}</caption>
          <thead>
            {table.getHeaderGroups().map((headerGroup) => (
              <tr key={headerGroup.id}>
                {headerGroup.headers.map((header) => {
                  const isSortable = header.column.getCanSort();
                  return (
                    <th
                      key={header.id}
                      scope="col"
                      aria-sort={
                        isSortable
                          ? header.column.getIsSorted() === "asc"
                            ? "ascending"
                            : header.column.getIsSorted() === "desc"
                              ? "descending"
                              : "none"
                          : undefined
                      }
                      colSpan={header.colSpan}
                      style={{
                        width:
                          header.getSize() !== 150
                            ? header.getSize()
                            : undefined,
                      }}
                    >
                      {header.isPlaceholder ? null : isSortable ? (
                        <button
                          type="button"
                          className="th-sortable"
                          onClick={header.column.getToggleSortingHandler()}
                        >
                          {flexRender(
                            header.column.columnDef.header,
                            header.getContext(),
                          )}
                          {isSortable && (
                            <span className="sort-icon">
                              {{
                                asc: <Icon name="chevronUp" size={14} />,
                                desc: <Icon name="chevronDown" size={14} />,
                              }[header.column.getIsSorted() as string] ?? (
                                <Icon name="chevronDown" size={14} /> // Add a default faint icon in CSS
                              )}
                            </span>
                          )}
                        </button>
                      ) : (
                        flexRender(
                          header.column.columnDef.header,
                          header.getContext(),
                        )
                      )}
                    </th>
                  );
                })}
              </tr>
            ))}
          </thead>
          <tbody>
            {isLoading ? (
              <tr>
                <td
                  colSpan={table.getVisibleLeafColumns().length}
                  style={{ padding: 0 }}
                >
                  <Skeleton.Table rows={5} cols={columns.length} />
                </td>
              </tr>
            ) : table.getRowModel().rows?.length ? (
              table.getRowModel().rows.map((row) => (
                <tr
                  key={row.id}
                  data-state={row.getIsSelected() ? "selected" : undefined}
                  className={row.getIsSelected() ? "row-selected" : ""}
                >
                  {row.getVisibleCells().map((cell) => (
                    <td
                      key={cell.id}
                      data-label={
                        typeof cell.column.columnDef.header === "string"
                          ? cell.column.columnDef.header
                          : cell.column.id === "select"
                            ? "Sélection"
                            : cell.column.id
                      }
                    >
                      {flexRender(
                        cell.column.columnDef.cell,
                        cell.getContext(),
                      )}
                    </td>
                  ))}
                </tr>
              ))
            ) : (
              <tr>
                <td
                  colSpan={table.getVisibleLeafColumns().length}
                  style={{ padding: "2rem" }}
                >
                  {emptyState || (
                    <div className="text-center">Aucun résultat.</div>
                  )}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      {!isLoading && (
        <ListPagination
          page={table.getState().pagination.pageIndex}
          size={table.getState().pagination.pageSize}
          count={table.getRowModel().rows.length}
          totalElements={data.length}
          totalPages={table.getPageCount()}
          label={itemLabel}
          onPage={table.setPageIndex}
          onSize={(value) => table.setPageSize(Number(value))}
        />
      )}
    </div>
  );
}
