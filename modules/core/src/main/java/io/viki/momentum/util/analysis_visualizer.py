"""Page through Analysis JSON with arrows, PageUp/PageDown or Previous/Next buttons."""
import argparse
import json
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path, nargs="?", default=Path("analysis.json"))
    parser.add_argument("-o", "--output", type=Path, help="Export every page to a PDF")
    parser.add_argument("--start", type=float, default=0, help="Start time in seconds")
    parser.add_argument("--end", type=float, help="End time in seconds")
    parser.add_argument("--exclude-nested", action="store_true", help="Hide total curves and show exclusive time (toggle with E)")
    args = parser.parse_args()
    try:
        import matplotlib
        if args.output:
            matplotlib.use("Agg")
        import matplotlib.pyplot as plt
        from matplotlib.widgets import Button
        from matplotlib.backends.backend_pdf import PdfPages
    except ImportError:
        parser.error("Install matplotlib: python -m pip install matplotlib")
    with args.input.open(encoding="utf-8") as file:
        data = json.load(file)
    processes = data.get("processes")
    if processes is None:
        names = dict.fromkeys(row["type"] for row in data["samples"])
        processes = [{"name": name, "samples": [row for row in data["samples"] if row["type"] == name]}
                     for name in names]
    children = {}
    for process in processes:
        for row in process["samples"]:
            parent = row.get("parent_span", 0)
            if parent:
                spans = children.setdefault(parent, {})
                spans[process["name"]] = spans.get(process["name"], 0) + row["duration_ms"]
    selected = []
    for process in processes:
        rows = [row for row in process["samples"] if row["start_ms"] >= args.start * 1000
                and (args.end is None or row["start_ms"] <= args.end * 1000)]
        if rows:
            selected.append({**process, "samples": rows})
    if not selected:
        parser.error("No samples in the selected time range")
    processes = selected

    def stats(process):
        values = sorted(row["duration_ms"] for row in process["samples"])
        return (f"n={len(values)} mean={sum(values)/len(values):.3f} ms "
                f"p99={values[min(len(values)-1, int(len(values)*0.99))]:.3f} ms max={values[-1]:.3f} ms")

    for process in processes:
        print(f"{process['name']}: {stats(process)}" +
              (" [unfinished]" if process.get("unfinished") else ""))
    print(f"Dropped samples: {data.get('dropped_samples', 0)}")
    figure, axes = plt.subplots(2, 1, figsize=(15, 8), sharex=True)
    figure.subplots_adjust(bottom=0.16, top=0.85, hspace=0.18)
    page = 0
    pages = len(processes) + 1
    exclude_nested = args.exclude_nested

    def redraw():
        for axis in axes:
            axis.clear()
        visible = sorted(processes, key=lambda p: sum(row["duration_ms"] for row in p["samples"]), reverse=True)[:12] if page == 0 else [processes[page - 1]]
        for process in visible:
            rows = process["samples"]
            times = [row["start_ms"]/1000 for row in rows]
            if not exclude_nested:
                axes[0].plot(times, [row["duration_ms"] for row in rows],
                             label=process["name"] + " total", linewidth=0.7)
            if page != 0 or exclude_nested:
                axes[0].plot(times, [row.get("exclusive_ms", row["duration_ms"]) for row in rows],
                             label=process["name"] + " self (nested excluded)", linewidth=0.9)
            if page != 0:
                names = dict.fromkeys(name for row in rows for name in children.get(row.get("span"), {}))
                for name in names:
                    axes[0].plot(times, [children.get(row.get("span"), {}).get(name, 0) for row in rows],
                                 label="child: " + name, linewidth=0.7)
            intervals = [row for row in rows if row.get("interval_ms", 0) > 0]
            if intervals:
                axes[1].plot([row["start_ms"]/1000 for row in intervals],
                             [row["interval_ms"] for row in intervals], label=process["name"], linewidth=0.7)
        title = "Overview (12 largest inclusive totals; all processes have their own page)" if page == 0 else (
            processes[page - 1]["name"] + "\n" + stats(processes[page - 1]))
        figure.suptitle(f"{args.input.name} | {page+1}/{pages} | {title}")
        axes[0].set_ylabel("Duration (ms)")
        axes[1].set_ylabel("Start-to-start interval (ms)")
        axes[1].set_xlabel("Elapsed time (s)")
        axes[1].set_xlim(min(row["start_ms"] for p in processes for row in p["samples"])/1000,
                        max(row["start_ms"] for p in processes for row in p["samples"])/1000 + 0.001)
        for axis in axes:
            axis.grid(True, alpha=0.3)
            if axis.lines:
                axis.legend(fontsize="small", loc="upper right")
        figure.canvas.draw_idle()

    def turn(amount):
        nonlocal page
        page = (page + amount) % pages
        redraw()

    def key(event):
        nonlocal page, exclude_nested
        if event.key in ("right", "pagedown", " "):
            turn(1)
        elif event.key in ("left", "pageup"):
            turn(-1)
        elif event.key == "home":
            page = 0
            redraw()
        elif event.key in ("e", "E"):
            exclude_nested = not exclude_nested
            redraw()

    if args.output:
        if args.output.suffix.lower() != ".pdf":
            parser.error("--output must be a .pdf file")
        with PdfPages(args.output) as pdf:
            for page in range(pages):
                redraw()
                pdf.savefig(figure)
        plt.close(figure)
        print(f"Saved {pages} pages to {args.output}")
    else:
        previous = Button(figure.add_axes((0.36, 0.035, 0.12, 0.05)), "Previous")
        following = Button(figure.add_axes((0.52, 0.035, 0.12, 0.05)), "Next")
        previous.on_clicked(lambda event: turn(-1))
        following.on_clicked(lambda event: turn(1))
        figure.canvas.mpl_connect("key_press_event", key)
        redraw()
        plt.show()


if __name__ == "__main__":
    main()
